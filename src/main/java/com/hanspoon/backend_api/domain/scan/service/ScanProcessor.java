package com.hanspoon.backend_api.domain.scan.service;

import com.hanspoon.backend_api.domain.ai.client.AiClient;
import com.hanspoon.backend_api.domain.ai.dto.ocr.OcrRequest;
import com.hanspoon.backend_api.domain.ai.dto.ocr.OcrResponse;
import com.hanspoon.backend_api.domain.ai.dto.result.FinalMenu;
import com.hanspoon.backend_api.domain.ai.dto.result.FinalResultResponse;
import com.hanspoon.backend_api.domain.ai.dto.ruleengine.RuleEngineRequest;
import com.hanspoon.backend_api.domain.ai.dto.ruleengine.RuleEngineResponse;
import com.hanspoon.backend_api.domain.ai.dto.ruleengine.RuleProfile;
import com.hanspoon.backend_api.domain.ai.mapper.AiProfileMapper;
import com.hanspoon.backend_api.domain.scan.entity.MenuAnalysis;
import com.hanspoon.backend_api.domain.scan.entity.ScanImage;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.upload.dto.VerifiedUpload;
import com.hanspoon.backend_api.domain.upload.service.S3StorageService;
import com.hanspoon.backend_api.domain.user.entity.User;
import com.hanspoon.backend_api.domain.user.entity.UserProfile;
import com.hanspoon.backend_api.domain.user.repository.UserProfileRepository;
import com.hanspoon.backend_api.domain.user.repository.UserRepository;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 검증된 S3 객체 정보 전달 → AI 서비스 호출 → 메뉴 분석 저장을 수행하는 비동기 스캔 처리기.
 * ScanProcessor는 전체 작업의 순서를 지휘함.
 *
 * <p> @Transactional 추가 금지.
 * DB 커넥션이 점유되어 비동기 워커 수만큼 커넥션 풀이 고갈되는 것 방지하기 위함.
 * 상태 전이는 {@link ScanStateWriter} 담당.
 *
 * <p>app.ai-service.pipeline: legacy(OCR → 룰엔진 → 결과, 기본) | analyze(Prepare → Judge, {@link AnalyzeScanFlow}).
 */
@Component
public class ScanProcessor {

    private static final Logger log = LoggerFactory.getLogger(ScanProcessor.class);
    private static final String NEEDS_RETAKE = "needs_retake";
    static final String ANALYZE_PIPELINE = "analyze";

    private final AiClient aiClient;
    private final S3StorageService s3StorageService;
    private final UserProfileRepository userProfileRepository;
    private final ScanSessionRepository scanSessionRepository;
    private final ScanStateWriter scanStateWriter;
    private final AnalyzeScanFlow analyzeScanFlow;
    private final UserRepository userRepository;
    private final boolean presignedFallbackEnabled;
    private final boolean analyzePipeline;

    public ScanProcessor(
            AiClient aiClient,
            S3StorageService s3StorageService,
            UserProfileRepository userProfileRepository,
            ScanSessionRepository scanSessionRepository,
            ScanStateWriter scanStateWriter,
            AnalyzeScanFlow analyzeScanFlow,
            UserRepository userRepository,
            @Value("${app.ai-service.presigned-url-fallback-enabled:true}") boolean presignedFallbackEnabled,
            @Value("${app.ai-service.pipeline:legacy}") String pipeline) {
        this.aiClient = aiClient;
        this.s3StorageService = s3StorageService;
        this.userProfileRepository = userProfileRepository;
        this.scanSessionRepository = scanSessionRepository;
        this.scanStateWriter = scanStateWriter;
        this.analyzeScanFlow = analyzeScanFlow;
        this.userRepository = userRepository;
        this.presignedFallbackEnabled = presignedFallbackEnabled;
        this.analyzePipeline = ANALYZE_PIPELINE.equals(pipeline);
    }

    @Async("scanTaskExecutor")
    public void process(UUID scanId, UUID userId, VerifiedUpload upload, String source) {
        long processingStartedAt = System.nanoTime();
        try {
            ScanSession session = scanSessionRepository.findById(scanId).orElse(null);
            if (session == null) {
                log.warn("Scan session not found, skip processing: {}", scanId);
                return;
            }
            Long storeId = session.getStoreId();
            if (analyzePipeline) {
                processAnalyze(scanId, userId, storeId, upload, source);
                log.info(
                        "Scan completed: {} (pipeline=analyze, totalMs={})",
                        scanId,
                        elapsedMillis(processingStartedAt));
                return;
            }

            String storageKey = upload.storageKey();
            String fallbackImageUrl =
                    presignedFallbackEnabled ? s3StorageService.createReadUrl(storageKey, upload.versionId()) : null;
            long ocrStartedAt = System.nanoTime();
            OcrResponse ocr = aiClient.requestOcr(
                    OcrRequest.forS3(storeId, source, storageKey, fallbackImageUrl, upload.versionId(), upload.eTag()));
            if (ocr == null) {
                throw new BusinessException(ErrorCode.OCR_SERVICE_ERROR, "OCR service returned an empty response.");
            }
            validateStoreContext(storeId, ocr.scanSession(), "ocr");
            logOcrCompleted(scanId, elapsedMillis(ocrStartedAt), ocr);

            scanStateWriter.applyOcrResult(
                    scanId,
                    toScanImage(scanId, source, upload, ocr),
                    ocr.scanSession() != null ? ocr.scanSession().menuCount() : null,
                    parseScannedAt(ocr.scanSession() != null ? ocr.scanSession().scannedAt() : null));

            if (isNeedsRetake(ocr)) {
                scanStateWriter.applyNeedsRetake(
                        scanId, ocr.scanQuality().reasons(), ocr.scanQuality().retakeSuggestions());
                log.info("Scan needs retake: {}", scanId);
                return;
            }

            RuleProfile ruleProfile = loadRuleProfile(userId);
            long ruleEngineStartedAt = System.nanoTime();
            RuleEngineResponse judged = aiClient.judge(new RuleEngineRequest(storeId, ruleProfile, ocr));
            if (judged == null) {
                throw new BusinessException(ErrorCode.RULE_ENGINE_ERROR, "Rule engine returned an empty response.");
            }
            validateStoreContext(storeId, judged.scanSession(), "rule_engine");
            logStageCompleted(scanId, "rule_engine", ruleEngineStartedAt);

            long resultStartedAt = System.nanoTime();
            FinalResultResponse finalResult = aiClient.result(judged);
            if (finalResult == null) {
                throw new BusinessException(
                        ErrorCode.RESULT_SERVICE_ERROR, "Result service returned an empty response.");
            }
            validateStoreContext(storeId, finalResult.scanSession(), "result");
            logStageCompleted(scanId, "result", resultStartedAt);

            Integer riskyCount =
                    judged.scanSession() != null ? judged.scanSession().riskyMenuCount() : null;
            scanStateWriter.complete(scanId, riskyCount, merge(scanId, ocr, finalResult));
            log.info("Scan completed: {} (totalMs={})", scanId, elapsedMillis(processingStartedAt));
        } catch (Exception exception) {
            log.error(
                    "Scan failed: {} (failureCode={}, totalMs={})",
                    scanId,
                    failureCode(exception),
                    elapsedMillis(processingStartedAt),
                    exception);
            markFailedQuietly(scanId, exception);
        }
    }

    /** 프로필·언어를 먼저 읽어 AI 호출 전에 실패. 재촬영·세션 삭제면 Judge 생략. */
    private void processAnalyze(UUID scanId, UUID userId, Long storeId, VerifiedUpload upload, String source) {
        RuleProfile ruleProfile = loadRuleProfile(userId);
        String locale = userRepository
                .findById(userId)
                .map(User::getLanguageCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        long prepareStartedAt = System.nanoTime();
        var prepared = analyzeScanFlow.prepare(scanId, upload, source, locale);
        logStageCompleted(scanId, "prepare", prepareStartedAt);
        if (prepared.isEmpty() || prepared.get().analyses().isEmpty()) {
            log.info("Scan stopped after prepare: {} (retake={})", scanId, prepared.isEmpty());
            return;
        }
        long judgeStartedAt = System.nanoTime();
        analyzeScanFlow.judge(scanId, storeId, prepared.get(), ruleProfile, locale);
        logStageCompleted(scanId, "judge", judgeStartedAt);
    }

    /** 단건 읽기 2회. */
    private RuleProfile loadRuleProfile(UUID userId) {
        UserProfile profile = userProfileRepository
                .findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND));
        return AiProfileMapper.toRuleProfile(profile);
    }

    private void markFailedQuietly(UUID scanId, Exception cause) {
        try {
            scanStateWriter.markFailed(scanId, failureCode(cause));
        } catch (Exception exception) {
            log.error("Failed to mark scan as FAILED: {} (cause: {})", scanId, cause.toString(), exception);
        }
    }

    private ScanImage toScanImage(UUID scanId, String source, VerifiedUpload upload, OcrResponse ocr) {
        String resolvedSource = source;
        if (resolvedSource == null && ocr.menuImage() != null) {
            resolvedSource = ocr.menuImage().source();
        }
        return ScanImage.create(
                scanId,
                resolvedSource,
                upload.storageKey(),
                s3StorageService.objectUri(upload.storageKey()),
                upload.contentType(),
                upload.contentLength(),
                upload.versionId(),
                upload.eTag());
    }

    private boolean isNeedsRetake(OcrResponse ocr) {
        return ocr.scanQuality() != null
                && NEEDS_RETAKE.equals(ocr.scanQuality().status());
    }

    /** menu_item_id 도입 전까지 개수·메뉴명·표시 순서를 검증한 뒤 배열 순서로 머지한다. */
    private List<MenuAnalysis> merge(UUID scanId, OcrResponse ocr, FinalResultResponse finalResult) {
        List<com.hanspoon.backend_api.domain.ai.dto.ocr.MenuAnalysis> ocrMenus =
                ocr.menuAnalyses() != null ? ocr.menuAnalyses() : List.of();
        List<FinalMenu> finalMenus =
                finalResult != null && finalResult.menuAnalyses() != null ? finalResult.menuAnalyses() : List.of();
        if (ocrMenus.size() != finalMenus.size()) {
            throw resultMismatch("menu count", ocrMenus.size(), finalMenus.size());
        }
        if (ocrMenus.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.AI_RESULT_MISMATCH, "Usable OCR response must contain at least one menu.");
        }

        Set<Integer> displayOrders = new HashSet<>();
        List<MenuAnalysis> merged = new ArrayList<>(ocrMenus.size());
        for (int i = 0; i < ocrMenus.size(); i++) {
            var o = ocrMenus.get(i);
            FinalMenu f = finalMenus.get(i);
            String ocrName = o == null ? "" : normalizeName(o.menuNameKo());
            String finalName = f == null ? "" : normalizeName(f.menuName());
            if (ocrName.isBlank() || finalName.isBlank() || !Objects.equals(ocrName, finalName)) {
                throw resultMismatch(
                        "menu name at index " + i, o == null ? null : o.menuNameKo(), f == null ? null : f.menuName());
            }
            if (f.riskLevel() == null) {
                throw new BusinessException(
                        ErrorCode.AI_RESULT_MISMATCH, "Final risk_level is missing at index " + i + ".");
            }
            int displayOrder = o.displayOrder() != null ? o.displayOrder() : i + 1;
            if (displayOrder <= 0 || !displayOrders.add(displayOrder)) {
                throw new BusinessException(
                        ErrorCode.AI_RESULT_MISMATCH, "Invalid or duplicate display_order: " + displayOrder);
            }
            merged.add(MenuAnalysis.create(
                    scanId,
                    displayOrder,
                    o.menuNameKo(),
                    o.menuNameEn(),
                    o.descriptionKo(),
                    o.descriptionEn(),
                    o.priceText(),
                    o.isSpicy(),
                    // AI 제공 URL은 출처·만료를 보장할 수 없다. 백엔드 소유의 크롭·서명 체계 도입 전에는 저장하지 않는다.
                    null,
                    f.riskLevel(),
                    f.hits(),
                    f.message(),
                    f.ownerCard()));
        }
        return merged;
    }

    private BusinessException resultMismatch(String field, Object ocrValue, Object finalValue) {
        return new BusinessException(
                ErrorCode.AI_RESULT_MISMATCH,
                "AI result mismatch: " + field + " (ocr=" + ocrValue + ", final=" + finalValue + ").");
    }

    private void validateStoreContext(
            Long expectedStoreId, com.hanspoon.backend_api.domain.ai.dto.ocr.ScanSession aiSession, String stage) {
        Long actualStoreId = aiSession == null ? null : aiSession.storeId();
        if (!Objects.equals(expectedStoreId, actualStoreId)) {
            throw resultMismatch(stage + " store_id", expectedStoreId, actualStoreId);
        }
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim();
    }

    private String failureCode(Exception exception) {
        if (exception instanceof BusinessException businessException) {
            return businessException.getErrorCode().getCode();
        }
        return ErrorCode.INTERNAL_SERVER_ERROR.getCode();
    }

    private void logOcrCompleted(UUID scanId, long backendDurationMs, OcrResponse ocr) {
        var quality = ocr.scanQuality();
        if (quality != null
                && quality.imageQuality() != null
                && Boolean.FALSE.equals(quality.imageQuality().available())
                && quality.imageQuality().error() != null) {
            log.warn(
                    "Image quality analysis unavailable: {} (reason={})",
                    scanId,
                    quality.imageQuality().error());
        }
        log.info(
                "OCR completed: {} (backendMs={}, aiMs={}, qualityStatus={}, score={}, rawLines={}, priceMatches={}, "
                        + "priceAnchors={}, pairCoverage={}, meanOcrConfidence={}, meanPairConfidence={}, "
                        + "imageWidth={}, imageHeight={}, imageQualityScore={}, attempts={}, preprocessingApplied={}, "
                        + "selectedAttempt={}, retrySkippedReason={}, fetchSource={}, aiQueueMs={})",
                scanId,
                backendDurationMs,
                quality != null ? quality.ocrProcessingTimeMs() : null,
                quality != null ? quality.status() : null,
                quality != null ? quality.score() : null,
                quality != null ? quality.rawLineCount() : null,
                quality != null ? quality.priceMatchCount() : null,
                quality != null ? quality.priceAnchorCount() : null,
                quality != null ? quality.pairCoverage() : null,
                quality != null ? quality.meanOcrConfidence() : null,
                quality != null ? quality.meanPairConfidence() : null,
                quality != null ? quality.imageWidth() : null,
                quality != null ? quality.imageHeight() : null,
                quality != null && quality.imageQuality() != null
                        ? quality.imageQuality().score()
                        : null,
                quality != null ? quality.ocrAttemptCount() : null,
                quality != null ? quality.preprocessingApplied() : null,
                quality != null ? quality.selectedOcrAttempt() : null,
                quality != null ? quality.retrySkippedReason() : null,
                quality != null ? quality.imageFetchSource() : null,
                quality != null ? quality.queueWaitMs() : null);
    }

    private void logStageCompleted(UUID scanId, String stage, long startedAt) {
        log.info("AI stage completed: {} (stage={}, durationMs={})", scanId, stage, elapsedMillis(startedAt));
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private Instant parseScannedAt(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            log.warn("Unparseable scanned_at '{}', fallback to now", value);
            return Instant.now();
        }
    }
}
