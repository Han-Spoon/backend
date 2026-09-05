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
import com.hanspoon.backend_api.domain.scan.entity.MenuImage;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import com.hanspoon.backend_api.domain.scan.repository.MenuAnalysisRepository;
import com.hanspoon.backend_api.domain.scan.repository.MenuImageRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.upload.service.S3StorageService;
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

@Component
public class ScanProcessor {

    private static final Logger log = LoggerFactory.getLogger(ScanProcessor.class);
    private static final String NEEDS_RETAKE = "needs_retake";

    private final AiClient aiClient;
    private final S3StorageService s3StorageService;
    private final UserProfileRepository userProfileRepository;
    private final UserAllergyRepository userAllergyRepository;
    private final ScanSessionRepository scanSessionRepository;
    private final MenuImageRepository menuImageRepository;
    private final MenuAnalysisRepository menuAnalysisRepository;

    public ScanProcessor(
            AiClient aiClient,
            S3StorageService s3StorageService,
            UserProfileRepository userProfileRepository,
            UserAllergyRepository userAllergyRepository,
            ScanSessionRepository scanSessionRepository,
            MenuImageRepository menuImageRepository,
            MenuAnalysisRepository menuAnalysisRepository) {
        this.aiClient = aiClient;
        this.s3StorageService = s3StorageService;
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
            // 1) presigned GET URL → OCR
            String imageUrl = s3StorageService.createReadUrl(storageKey);
            OcrResponse ocr = aiClient.requestOcr(new OcrRequest(source, storageKey, imageUrl));

            // 2) menu_image 저장 + 세션에 OCR 메타 반영
            persistMenuImage(scanId, source, storageKey, ocr);
            session.applyOcrResult(
                    ocr.scanSession() != null ? ocr.scanSession().menuCount() : null,
                    parseScannedAt(ocr.scanSession() != null ? ocr.scanSession().scannedAt() : null));

            // 3) needs_retake → 종료 (OCR 이 준 사유 저장)
            if (isNeedsRetake(ocr)) {
                session.applyNeedsRetake(
                        ocr.scanQuality() != null ? ocr.scanQuality().reasons() : null);
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

            // 5) 최종 결과(ai_result) — message/owner_card 생성(GPT는 AI 내부)
            FinalResultResponse finalResult = aiClient.result(judged);

            // 6) OCR ↔ Final index 머지 → 저장 (표시 출처는 ai_result FinalOutput)
            menuAnalysisRepository.saveAll(merge(scanId, ocr, finalResult));

            // 7) 세션 완료
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
                scanId, resolvedSource, storageKey, s3StorageService.objectUri(storageKey), mimeType, fileSize));
    }

    private boolean isNeedsRetake(OcrResponse ocr) {
        return ocr.scanQuality() != null
                && NEEDS_RETAKE.equals(ocr.scanQuality().status());
    }

    private List<MenuAnalysis> merge(UUID scanId, OcrResponse ocr, FinalResultResponse finalResult) {
        List<com.hanspoon.backend_api.domain.ai.dto.ocr.MenuAnalysis> ocrMenus =
                ocr.menuAnalyses() != null ? ocr.menuAnalyses() : List.of();
        List<FinalMenu> finalMenus =
                finalResult != null && finalResult.menuAnalyses() != null ? finalResult.menuAnalyses() : List.of();
        if (ocrMenus.size() != finalMenus.size()) {
            log.warn("menu size mismatch: ocr={}, final={}", ocrMenus.size(), finalMenus.size());
        }
        int n = Math.min(ocrMenus.size(), finalMenus.size());
        List<MenuAnalysis> merged = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            var o = ocrMenus.get(i);
            FinalMenu f = finalMenus.get(i);
            merged.add(MenuAnalysis.create(
                    scanId,
                    o.displayOrder() != null ? o.displayOrder() : i + 1,
                    o.menuNameKo(),
                    o.menuNameEn(),
                    o.descriptionKo(),
                    o.descriptionEn(),
                    o.priceText(),
                    o.isSpicy(),
                    o.imageUrl(),
                    f.riskLevel(),
                    f.hits(),
                    f.message(),
                    f.ownerCard()));
        }
        return merged;
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
