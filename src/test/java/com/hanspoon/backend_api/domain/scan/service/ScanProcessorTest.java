package com.hanspoon.backend_api.domain.scan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hanspoon.backend_api.domain.scan.entity.MenuAnalysis;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.store.entity.StoreMatchMethod;
import com.hanspoon.backend_api.domain.upload.dto.VerifiedUpload;
import com.hanspoon.backend_api.domain.user.entity.ReligionType;
import com.hanspoon.backend_api.domain.user.entity.User;
import com.hanspoon.backend_api.domain.user.entity.UserProfile;
import com.hanspoon.backend_api.domain.user.repository.UserProfileRepository;
import com.hanspoon.backend_api.domain.user.repository.UserRepository;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScanProcessorTest {

    private static final String STORAGE_KEY = "scans/11111111-1111-1111-1111-111111111111/abc.jpg";
    private static final Long STORE_ID = 42L;
    private static final VerifiedUpload VERIFIED_UPLOAD =
            new VerifiedUpload(STORAGE_KEY, "version-1", "\"etag-1\"", 123L, "image/jpeg");

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private ScanSessionRepository scanSessionRepository;

    @Mock
    private AnalyzeScanFlow analyzeScanFlow;

    @Mock
    private UserRepository userRepository;

    private ScanProcessor scanProcessor;

    // ScanStateWriter 는 실제 객체. 실패 기록까지 함께 검증.
    @BeforeEach
    void setUp() {
        scanProcessor = new ScanProcessor(
                userProfileRepository,
                scanSessionRepository,
                new ScanStateWriter(scanSessionRepository),
                analyzeScanFlow,
                userRepository);
    }

    @Test
    void runsPrepareThenJudge() {
        UUID userId = UUID.randomUUID();
        ScanSession session = processingSession(userId);
        UUID scanId = session.getId();
        givenUser(userId, session, "ja");
        var prepared = new AnalyzeScanFlow.PreparedScan(
                null, List.of(), List.of(MenuAnalysis.prepared(scanId, 0, "김치찌개", null, null, null, null, null, null)));
        when(analyzeScanFlow.prepare(scanId, VERIFIED_UPLOAD, "camera", "ja")).thenReturn(Optional.of(prepared));

        scanProcessor.process(scanId, userId, VERIFIED_UPLOAD, "camera");

        verify(analyzeScanFlow).judge(eq(scanId), eq(STORE_ID), eq(prepared), any(), eq("ja"));
    }

    @Test
    void skipsJudgeWhenRetakeIsNeeded() {
        UUID userId = UUID.randomUUID();
        ScanSession session = processingSession(userId);
        UUID scanId = session.getId();
        givenUser(userId, session, "ko");
        when(analyzeScanFlow.prepare(any(), any(), any(), any())).thenReturn(Optional.empty());

        scanProcessor.process(scanId, userId, VERIFIED_UPLOAD, null);

        verify(analyzeScanFlow, never()).judge(any(), any(), any(), any(), any());
    }

    @Test
    void failsBeforeAiCallWithoutProfile() {
        UUID userId = UUID.randomUUID();
        ScanSession session = processingSession(userId);
        UUID scanId = session.getId();
        when(scanSessionRepository.findById(scanId)).thenReturn(Optional.of(session));
        when(userProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());

        scanProcessor.process(scanId, userId, VERIFIED_UPLOAD, null);

        assertThat(session.getScanStatus()).isEqualTo(ScanStatus.FAILED);
        assertThat(session.getFailureCode()).isEqualTo(ErrorCode.PROFILE_NOT_FOUND.getCode());
        verifyNoInteractions(analyzeScanFlow);
    }

    @Test
    void marksFailedWithAiErrorCode() {
        UUID userId = UUID.randomUUID();
        ScanSession session = processingSession(userId);
        UUID scanId = session.getId();
        givenUser(userId, session, "ko");
        when(analyzeScanFlow.prepare(any(), any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.PREPARE_SERVICE_ERROR, "boom"));

        scanProcessor.process(scanId, userId, VERIFIED_UPLOAD, "upload");

        assertThat(session.getScanStatus()).isEqualTo(ScanStatus.FAILED);
        assertThat(session.getFailureCode()).isEqualTo(ErrorCode.PREPARE_SERVICE_ERROR.getCode());
        verify(analyzeScanFlow, never()).judge(any(), any(), any(), any(), any());
    }

    private void givenUser(UUID userId, ScanSession session, String languageCode) {
        when(scanSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(userProfileRepository.findByUserId(userId))
                .thenReturn(Optional.of(UserProfile.create(
                        userId, "KR", false, false, null, ReligionType.HALAL, true, true, List.of())));
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.create("a@example.com", "a", languageCode)));
    }

    private static ScanSession processingSession(UUID userId) {
        return ScanSession.start(userId, STORAGE_KEY, STORE_ID, "한스푼", StoreMatchMethod.GPS_CANDIDATE);
    }
}
