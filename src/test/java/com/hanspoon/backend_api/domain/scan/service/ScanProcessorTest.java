package com.hanspoon.backend_api.domain.scan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hanspoon.backend_api.domain.ai.client.AiClient;
import com.hanspoon.backend_api.domain.ai.dto.common.EscalationCase;
import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.domain.ai.dto.ocr.OcrResponse;
import com.hanspoon.backend_api.domain.ai.dto.result.FinalMenu;
import com.hanspoon.backend_api.domain.ai.dto.result.FinalMessage;
import com.hanspoon.backend_api.domain.ai.dto.result.FinalResultResponse;
import com.hanspoon.backend_api.domain.ai.dto.result.OwnerCard;
import com.hanspoon.backend_api.domain.ai.dto.result.OwnerQuestion;
import com.hanspoon.backend_api.domain.ai.dto.ruleengine.RiskReason;
import com.hanspoon.backend_api.domain.ai.dto.ruleengine.RuleEngineResponse;
import com.hanspoon.backend_api.domain.ai.dto.ruleengine.RuleMenuAnalysis;
import com.hanspoon.backend_api.domain.scan.entity.MenuAnalysis;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import com.hanspoon.backend_api.domain.scan.repository.MenuAnalysisRepository;
import com.hanspoon.backend_api.domain.scan.repository.MenuImageRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.upload.service.S3StorageService;
import com.hanspoon.backend_api.domain.user.entity.ReligionType;
import com.hanspoon.backend_api.domain.user.entity.UserProfile;
import com.hanspoon.backend_api.domain.user.repository.UserAllergyRepository;
import com.hanspoon.backend_api.domain.user.repository.UserProfileRepository;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScanProcessorTest {

    private static final String STORAGE_KEY = "scans/11111111-1111-1111-1111-111111111111/abc.jpg";

    @Mock
    private AiClient aiClient;

    @Mock
    private S3StorageService s3StorageService;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private UserAllergyRepository userAllergyRepository;

    @Mock
    private ScanSessionRepository scanSessionRepository;

    @Mock
    private MenuImageRepository menuImageRepository;

    @Mock
    private MenuAnalysisRepository menuAnalysisRepository;

    @InjectMocks
    private ScanProcessor scanProcessor;

    private static com.hanspoon.backend_api.domain.ai.dto.ocr.MenuAnalysis ocrMenu(
            String name, String price, boolean spicy, int order) {
        return new com.hanspoon.backend_api.domain.ai.dto.ocr.MenuAnalysis(
                name, null, "", null, price, null, spicy, null, order);
    }

    private OcrResponse usableOcr() {
        return new OcrResponse(
                new com.hanspoon.backend_api.domain.ai.dto.ocr.ScanSession(
                        "menu.jpg", 2, null, "completed", "2026-06-05T00:00:00Z"),
                new com.hanspoon.backend_api.domain.ai.dto.ocr.MenuImage(
                        "upload", STORAGE_KEY, "https://s3/presigned", "image/jpeg", 123L),
                new com.hanspoon.backend_api.domain.ai.dto.ocr.ScanQuality(
                        "usable", 80, 20, 2, 1.0, 1280, 960, null, List.of(), List.of()),
                List.of(ocrMenu("samgyeopsal", "9000", false, 1), ocrMenu("doenjang", "8000", true, 2)),
                null);
    }

    @Test
    void completesScanAndMergesOcrWithRuleEngine() {
        UUID userId = UUID.randomUUID();
        ScanSession session = ScanSession.create(userId, "menu.jpg", null, null, ScanStatus.PROCESSING, null);
        UUID scanId = session.getId();
        OcrResponse ocr = usableOcr();

        when(scanSessionRepository.findById(scanId)).thenReturn(Optional.of(session));
        when(s3StorageService.createReadUrl(STORAGE_KEY)).thenReturn("https://s3/presigned?X-Amz-Signature=r");
        when(s3StorageService.objectUri(STORAGE_KEY)).thenReturn("s3://test-bucket/" + STORAGE_KEY);
        when(aiClient.requestOcr(any())).thenReturn(ocr);
        UserProfile profile = UserProfile.create(userId, "KR", false, false, null, ReligionType.HALAL, true, true);
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
        when(userAllergyRepository.findByUserProfileId(profile.getId())).thenReturn(List.of());
        RuleEngineResponse judged = new RuleEngineResponse(
                new com.hanspoon.backend_api.domain.ai.dto.ocr.ScanSession("menu.jpg", 2, 1, "completed", null),
                ocr.menuImage(),
                ocr.scanQuality(),
                List.of(
                        new RuleMenuAnalysis(
                                "samgyeopsal",
                                false,
                                RiskLevel.DANGER,
                                List.of("is_pork"),
                                List.of(),
                                List.of("is_pork"),
                                false,
                                List.of(),
                                null,
                                List.of(new RiskReason("halal", "pork included"))),
                        new RuleMenuAnalysis(
                                "doenjang",
                                true,
                                RiskLevel.CAUTION,
                                List.of(),
                                List.of("has_unclear_broth"),
                                List.of("is_pork"),
                                true,
                                List.of(EscalationCase.AMBIGUITY),
                                null,
                                null)));
        when(aiClient.judge(any())).thenReturn(judged);
        FinalResultResponse finalResult = new FinalResultResponse(List.of(
                new FinalMenu(
                        "samgyeopsal",
                        RiskLevel.DANGER,
                        List.of("is_pork"),
                        new FinalMessage("pork included", null, null),
                        null),
                new FinalMenu(
                        "doenjang",
                        RiskLevel.CAUTION,
                        List.of(),
                        new FinalMessage("broth unclear", null, null),
                        new OwnerCard(
                                "doenjang", "has_unclear_broth", new OwnerQuestion("use anchovy?", null, null)))));
        when(aiClient.result(any())).thenReturn(finalResult);

        scanProcessor.process(scanId, userId, STORAGE_KEY, "upload");

        assertThat(session.getScanStatus()).isEqualTo(ScanStatus.COMPLETED);
        assertThat(session.getMenuCount()).isEqualTo(2);
        assertThat(session.getRiskyMenuCount()).isEqualTo(1);
        verify(menuImageRepository).save(any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MenuAnalysis>> captor = ArgumentCaptor.forClass(List.class);
        verify(menuAnalysisRepository).saveAll(captor.capture());
        List<MenuAnalysis> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        // OCR 가격 + FinalOutput 위험도/태그가 같은 행에 머지됐는지
        assertThat(saved.get(0).getMenuNameKo()).isEqualTo("samgyeopsal");
        assertThat(saved.get(0).getPriceText()).isEqualTo("9000");
        assertThat(saved.get(0).getRiskLevel()).isEqualTo(RiskLevel.DANGER);
        assertThat(saved.get(0).getHitTags()).containsExactly("is_pork");
        assertThat(saved.get(0).getDisplayOrder()).isEqualTo(1);
        assertThat(saved.get(1).getRiskLevel()).isEqualTo(RiskLevel.CAUTION);
        // ai_result 최종 표시 필드 머지
        assertThat(saved.get(0).getMessage().ko()).isEqualTo("pork included");
        assertThat(saved.get(1).getOwnerCard().question().ko()).isEqualTo("use anchovy?");
    }

    @Test
    void marksNeedsRetakeAndSkipsRuleEngine() {
        UUID userId = UUID.randomUUID();
        ScanSession session = ScanSession.create(userId, "menu.jpg", null, null, ScanStatus.PROCESSING, null);
        UUID scanId = session.getId();
        OcrResponse ocr = new OcrResponse(
                new com.hanspoon.backend_api.domain.ai.dto.ocr.ScanSession("menu.jpg", 0, null, "completed", null),
                new com.hanspoon.backend_api.domain.ai.dto.ocr.MenuImage("upload", STORAGE_KEY, "u", "image/jpeg", 1L),
                new com.hanspoon.backend_api.domain.ai.dto.ocr.ScanQuality(
                        "needs_retake", 20, 1, 0, 0.0, 100, 100, null, List.of(), List.of("too blurry")),
                List.of(),
                null);

        when(scanSessionRepository.findById(scanId)).thenReturn(Optional.of(session));
        when(s3StorageService.createReadUrl(STORAGE_KEY)).thenReturn("https://s3/presigned?X-Amz-Signature=r");
        when(s3StorageService.objectUri(STORAGE_KEY)).thenReturn("s3://test-bucket/" + STORAGE_KEY);
        when(aiClient.requestOcr(any())).thenReturn(ocr);

        scanProcessor.process(scanId, userId, STORAGE_KEY, "upload");

        assertThat(session.getScanStatus()).isEqualTo(ScanStatus.NEEDS_RETAKE);
        assertThat(session.getRetakeReasons()).containsExactly("too blurry");
        verify(aiClient, never()).judge(any());
        verify(menuAnalysisRepository, never()).saveAll(any());
    }

    @Test
    void marksFailedWhenOcrThrows() {
        UUID userId = UUID.randomUUID();
        ScanSession session = ScanSession.create(userId, "menu.jpg", null, null, ScanStatus.PROCESSING, null);
        UUID scanId = session.getId();

        when(scanSessionRepository.findById(scanId)).thenReturn(Optional.of(session));
        when(s3StorageService.createReadUrl(STORAGE_KEY)).thenReturn("https://s3/presigned?X-Amz-Signature=r");
        when(aiClient.requestOcr(any())).thenThrow(new BusinessException(ErrorCode.OCR_SERVICE_ERROR, "boom"));

        scanProcessor.process(scanId, userId, STORAGE_KEY, "upload");

        assertThat(session.getScanStatus()).isEqualTo(ScanStatus.FAILED);
        verify(menuAnalysisRepository, never()).saveAll(any());
    }
}
