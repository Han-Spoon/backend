package com.hanspoon.backend_api.domain.scan.service;

import com.hanspoon.backend_api.domain.ai.client.AiClient;
import com.hanspoon.backend_api.domain.ai.dto.common.EscalationCase;
import com.hanspoon.backend_api.domain.ai.dto.ocr.OcrRequest;
import com.hanspoon.backend_api.domain.ai.dto.ocr.OcrResponse;
import com.hanspoon.backend_api.domain.ai.dto.ruleengine.RuleEngineRequest;
import com.hanspoon.backend_api.domain.ai.dto.ruleengine.RuleEngineResponse;
import com.hanspoon.backend_api.domain.ai.dto.ruleengine.RuleMenuAnalysis;
import com.hanspoon.backend_api.domain.ai.dto.ruleengine.RuleProfile;
import com.hanspoon.backend_api.domain.ai.mapper.AiProfileMapper;
import com.hanspoon.backend_api.domain.scan.entity.MenuAnalysis;
import com.hanspoon.backend_api.domain.scan.entity.MenuImage;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import com.hanspoon.backend_api.domain.scan.repository.MenuAnalysisRepository;
import com.hanspoon.backend_api.domain.scan.repository.MenuImageRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.upload.service.BlobStorageService;
import com.hanspoon.backend_api.domain.user.entity.UserAllergy;
import com.hanspoon.backend_api.domain.user.entity.UserProfile;
import com.hanspoon.backend_api.domain.user.repository.UserAllergyRepository;
import com.hanspoon.backend_api.domain.user.repository.UserProfileRepository;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스캔 비동기 파이프라인. read SAS 생성 → OCR → (needs_retake 분기) → 프로필 매핑 → RuleEngine →
 * OCR↔Rule index 머지 → 영속화. {@link ScanService} 와 분리된 빈이라 @Async 프록시가 정상 적용된다.
 *
 * <p>실패해도 예외를 호출자에게 던지지 않고 scan_status 를 FAILED 로 남긴다(폴링으로 확인).
 */
@Component
public class ScanProcessor {

    private static final Logger log = LoggerFactory.getLogger(ScanProcessor.class);
    private static final String NEEDS_RETAKE = "needs_retake";

    private final AiClient aiClient;
    private final BlobStorageService blobStorageService;
    private final UserProfileRepository userProfileRepository;
    private final UserAllergyRepository userAllergyRepository;
    private final ScanSessionRepository scanSessionRepository;
    private final MenuImageRepository menuImageRepository;
    private final MenuAnalysisRepository menuAnalysisRepository;

    public ScanProcessor(
            AiClient aiClient,
            BlobStorageService blobStorageService,
            UserProfileRepository userProfileRepository,
            UserAllergyRepository userAllergyRepository,
            ScanSessionRepository scanSessionRepository,
            MenuImageRepository menuImageRepository,
            MenuAnalysisRepository menuAnalysisRepository) {
        this.aiClient = aiClient;
        this.blobStorageService = blobStorageService;
        this.userProfileRepository = userProfileRepository;
        this.userAllergyRepository = userAllergyRepository;
        this.scanSessionRepository = scanSessionRepository;
        this.menuImageRepository = menuImageRepository;
        this.menuAnalysisRepository = menuAnalysisRepository;
    }

    @Async("applicationTaskExecutor")
    @Transactional
    public void process(UUID scanId, UUID userId, String storageKey, String source) {
        ScanSession session = scanSessionRepository.findById(scanId).orElse(null);
        if (session == null) {
            log.warn("Scan session not found, skip processing: {}", scanId);
            return;
        }
        try {
            // 1) read SAS → OCR
            String imageUrl = blobStorageService.createReadSasUrl(storageKey);
            OcrResponse ocr = aiClient.requestOcr(new OcrRequest(source, storageKey, imageUrl));

            // 2) menu_image 저장 + 세션에 OCR 메타 반영
            persistMenuImage(scanId, source, storageKey, ocr);
            session.applyOcrResult(
                    ocr.scanSession() != null ? ocr.scanSession().menuCount() : null,
                    parseScannedAt(ocr.scanSession() != null ? ocr.scanSession().scannedAt() : null));

            // 3) needs_retake → 종료
            if (isNeedsRetake(ocr)) {
                session.changeStatus(ScanStatus.NEEDS_RETAKE);
                log.info("Scan needs retake: {}", scanId);
                return;
            }

            // 4) 프로필 매핑 → RuleEngine
            UserProfile profile = userProfileRepository
                    .findByUserId(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PROFILE_NOT_FOUND));
            List<UserAllergy> allergies = userAllergyRepository.findByUserProfileId(profile.getId());
            RuleProfile ruleProfile = AiProfileMapper.toRuleProfile(profile, allergies);

            RuleEngineResponse judged = aiClient.judge(new RuleEngineRequest(ruleProfile, ocr));

            // 5) OCR ↔ Rule index 머지 → 저장
            menuAnalysisRepository.saveAll(merge(scanId, ocr, judged));

            // 6) 세션 완료
            Integer riskyCount =
                    judged.scanSession() != null ? judged.scanSession().riskyMenuCount() : null;
            session.applyRuleEngineResult(riskyCount, ScanStatus.COMPLETED);
            log.info("Scan completed: {} ({} menus)", scanId, session.getMenuCount());
        } catch (Exception exception) {
            log.error("Scan failed: {}", scanId, exception);
            session.changeStatus(ScanStatus.FAILED);
        }
    }

    private void persistMenuImage(UUID scanId, String source, String storageKey, OcrResponse ocr) {
        String resolvedSource = source;
        String mimeType = null;
        Long fileSize = null;
        if (ocr.menuImage() != null) {
            if (resolvedSource == null) {
                resolvedSource = ocr.menuImage().source();
            }
            mimeType = ocr.menuImage().mimeType();
            fileSize = ocr.menuImage().fileSize();
        }
        menuImageRepository.save(MenuImage.create(
                scanId, resolvedSource, storageKey, blobStorageService.blobUrl(storageKey), mimeType, fileSize));
    }

    private boolean isNeedsRetake(OcrResponse ocr) {
        return ocr.scanQuality() != null
                && NEEDS_RETAKE.equals(ocr.scanQuality().status());
    }

    private List<MenuAnalysis> merge(UUID scanId, OcrResponse ocr, RuleEngineResponse judged) {
        List<com.hanspoon.backend_api.domain.ai.dto.ocr.MenuAnalysis> ocrMenus =
                ocr.menuAnalyses() != null ? ocr.menuAnalyses() : List.of();
        List<RuleMenuAnalysis> ruleMenus = judged.menuAnalyses() != null ? judged.menuAnalyses() : List.of();
        if (ocrMenus.size() != ruleMenus.size()) {
            log.warn("OCR/Rule menu size mismatch: ocr={}, rule={}", ocrMenus.size(), ruleMenus.size());
        }
        int n = Math.min(ocrMenus.size(), ruleMenus.size());
        List<MenuAnalysis> merged = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            var o = ocrMenus.get(i);
            RuleMenuAnalysis r = ruleMenus.get(i);
            merged.add(MenuAnalysis.create(
                    scanId,
                    o.displayOrder() != null ? o.displayOrder() : i + 1,
                    o.menuNameKo(),
                    o.menuNameEn(),
                    o.descriptionKo(),
                    o.descriptionEn(),
                    o.priceText(),
                    r.isSpicy() != null ? r.isSpicy() : o.isSpicy(),
                    o.imageUrl(),
                    r.riskLevel(),
                    r.needGpt(),
                    r.hitTags(),
                    r.triggeredFlags(),
                    r.forbiddenTags(),
                    toCodes(r.escalationCase()),
                    r.gptContext(),
                    r.riskReasons()));
        }
        return merged;
    }

    private List<String> toCodes(List<EscalationCase> cases) {
        return cases == null
                ? null
                : cases.stream().map(EscalationCase::getCode).toList();
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
