package com.hanspoon.backend_api.domain.scan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.domain.consent.service.ConsentService;
import com.hanspoon.backend_api.domain.scan.dto.ScanCreatedResponse;
import com.hanspoon.backend_api.domain.scan.dto.ScanHistoryItem;
import com.hanspoon.backend_api.domain.scan.dto.ScanRecordResponse;
import com.hanspoon.backend_api.domain.scan.dto.ScanStoreSummary;
import com.hanspoon.backend_api.domain.scan.dto.StaffQuestionResult;
import com.hanspoon.backend_api.domain.scan.dto.StartScanRequest;
import com.hanspoon.backend_api.domain.scan.dto.UpdateScanTitleRequest;
import com.hanspoon.backend_api.domain.scan.entity.MenuAnalysis;
import com.hanspoon.backend_api.domain.scan.entity.ScanRecord;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import com.hanspoon.backend_api.domain.scan.repository.MenuAnalysisRepository;
import com.hanspoon.backend_api.domain.scan.repository.MenuTranslationProjection;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.scan.repository.StaffQuestionRepository;
import com.hanspoon.backend_api.domain.scan.repository.StaffQuestionRepository.MenuStaffQuestion;
import com.hanspoon.backend_api.domain.scan.service.MenuPhotoResolver.MenuPhoto;
import com.hanspoon.backend_api.domain.store.entity.Store;
import com.hanspoon.backend_api.domain.store.entity.StoreMatchMethod;
import com.hanspoon.backend_api.domain.store.entity.StoreStatus;
import com.hanspoon.backend_api.domain.store.repository.StoreRepository;
import com.hanspoon.backend_api.domain.upload.dto.VerifiedUpload;
import com.hanspoon.backend_api.domain.upload.service.S3StorageService;
import com.hanspoon.backend_api.global.common.PageResponse;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ScanServiceTest {

    @Mock
    private S3StorageService s3StorageService;

    @Mock
    private ScanSessionRepository scanSessionRepository;

    @Mock
    private MenuAnalysisRepository menuAnalysisRepository;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private ScanRecordService scanRecordService;

    @Mock
    private ScanProcessor scanProcessor;

    @Mock
    private ScanStateWriter scanStateWriter;

    @Mock
    private ConsentService consentService;

    @Mock
    private MenuPhotoResolver menuPhotoResolver;

    @Mock
    private StaffQuestionRepository staffQuestionRepository;

    @InjectMocks
    private ScanService scanService;

    @Test
    void startScanSavesSessionTriggersProcessorAndReturnsProcessing() {
        UUID userId = UUID.randomUUID();
        String key = "scans/" + userId + "/2f1c9d3e-0000-4000-8000-000000000001.jpg";
        VerifiedUpload upload = new VerifiedUpload(key, "version-1", "\"etag-1\"", 123L, "image/jpeg");
        Store store = store(42L, "한스푼");
        when(s3StorageService.resolveKey(userId, key)).thenReturn(key);
        when(storeRepository.findByIdAndStatus(42L, StoreStatus.ACTIVE)).thenReturn(Optional.of(store));
        when(s3StorageService.verifyUploadObject(key)).thenReturn(upload);
        when(scanSessionRepository.saveAndFlush(any(ScanSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, ScanSession.class));

        ScanCreatedResponse response =
                scanService.startScan(userId, new StartScanRequest(key, "upload", 42L, StoreMatchMethod.GPS_CANDIDATE));

        assertThat(response.status()).isEqualTo(ScanStatus.PROCESSING);
        assertThat(response.scanId()).isNotNull();
        verify(scanSessionRepository)
                .saveAndFlush(org.mockito.ArgumentMatchers.argThat(
                        session -> session.getStoreId().equals(42L)
                                && session.getStoreNameSnapshot().equals("한스푼")
                                && session.getStoreMatchMethod() == StoreMatchMethod.GPS_CANDIDATE));
        verify(scanProcessor).process(eq(response.scanId()), eq(userId), eq(upload), eq("upload"));
    }

    @Test
    void startScanAllowsUserToContinueWithoutStore() {
        UUID userId = UUID.randomUUID();
        String key = "scans/" + userId + "/without-store.jpg";
        VerifiedUpload upload = new VerifiedUpload(key, "version-1", "\"etag-1\"", 123L, "image/jpeg");
        when(s3StorageService.resolveKey(userId, key)).thenReturn(key);
        when(s3StorageService.verifyUploadObject(key)).thenReturn(upload);
        when(scanSessionRepository.saveAndFlush(any(ScanSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, ScanSession.class));

        ScanCreatedResponse response = scanService.startScan(userId, new StartScanRequest(key, "upload", null, null));

        assertThat(response.status()).isEqualTo(ScanStatus.PROCESSING);
        verify(storeRepository, never()).findByIdAndStatus(any(), any());
        verify(scanSessionRepository)
                .saveAndFlush(org.mockito.ArgumentMatchers.argThat(session -> session.getStoreId() == null
                        && session.getStoreNameSnapshot() == null
                        && session.getStoreMatchMethod() == null));
        verify(scanProcessor).process(eq(response.scanId()), eq(userId), eq(upload), eq("upload"));
    }

    @Test
    void startScanReturnsExistingSessionForTheSameStorageKey() {
        UUID userId = UUID.randomUUID();
        String key = "scans/" + userId + "/same.jpg";
        ScanSession existing = ScanSession.start(userId, key, 42L, "한스푼", StoreMatchMethod.GPS_CANDIDATE);
        when(s3StorageService.resolveKey(userId, key)).thenReturn(key);
        when(scanSessionRepository.findByUserIdAndStorageKey(userId, key)).thenReturn(Optional.of(existing));

        ScanCreatedResponse response =
                scanService.startScan(userId, new StartScanRequest(key, "upload", 42L, StoreMatchMethod.GPS_CANDIDATE));

        assertThat(response.scanId()).isEqualTo(existing.getId());
        verify(s3StorageService, never()).verifyUploadObject(any());
        verify(scanProcessor, never()).process(any(), any(), any(), any());
    }

    @Test
    void concurrentDuplicateStartReturnsTheSessionCreatedByTheWinner() {
        UUID userId = UUID.randomUUID();
        String key = "scans/" + userId + "/race.jpg";
        VerifiedUpload upload = new VerifiedUpload(key, "version-1", "\"etag-1\"", 123L, "image/jpeg");
        Store store = store(42L, "한스푼");
        ScanSession winner = ScanSession.start(userId, key, 42L, "한스푼", StoreMatchMethod.GPS_CANDIDATE);
        when(s3StorageService.resolveKey(userId, key)).thenReturn(key);
        when(scanSessionRepository.findByUserIdAndStorageKey(userId, key))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(storeRepository.findByIdAndStatus(42L, StoreStatus.ACTIVE)).thenReturn(Optional.of(store));
        when(s3StorageService.verifyUploadObject(key)).thenReturn(upload);
        when(scanSessionRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate storage key"));

        ScanCreatedResponse response =
                scanService.startScan(userId, new StartScanRequest(key, "upload", 42L, StoreMatchMethod.GPS_CANDIDATE));

        assertThat(response.scanId()).isEqualTo(winner.getId());
        verify(scanProcessor, never()).process(any(), any(), any(), any());
    }

    @Test
    void startScanRejectsMissingOrInactiveStoreBeforeVerifyingUpload() {
        UUID userId = UUID.randomUUID();
        String key = "scans/" + userId + "/missing-store.jpg";
        when(s3StorageService.resolveKey(userId, key)).thenReturn(key);
        when(storeRepository.findByIdAndStatus(404L, StoreStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scanService.startScan(
                        userId, new StartScanRequest(key, "upload", 404L, StoreMatchMethod.NAME_SEARCH)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.STORE_NOT_FOUND);

        verify(s3StorageService, never()).verifyUploadObject(any());
        verify(scanProcessor, never()).process(any(), any(), any(), any());
    }

    @Test
    void duplicateStorageKeyWithDifferentStoreContextReturnsConflict() {
        UUID userId = UUID.randomUUID();
        String key = "scans/" + userId + "/different-store.jpg";
        ScanSession existing = ScanSession.start(userId, key, 42L, "한스푼", StoreMatchMethod.GPS_CANDIDATE);
        when(s3StorageService.resolveKey(userId, key)).thenReturn(key);
        when(scanSessionRepository.findByUserIdAndStorageKey(userId, key)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> scanService.startScan(
                        userId, new StartScanRequest(key, "upload", 43L, StoreMatchMethod.GPS_CANDIDATE)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.SCAN_REQUEST_CONFLICT);

        verify(s3StorageService, never()).verifyUploadObject(any());
        verify(scanProcessor, never()).process(any(), any(), any(), any());
    }

    @Test
    void startScanDeletesSessionAndReturnsCapacityErrorWhenExecutorRejects() {
        UUID userId = UUID.randomUUID();
        String key = "scans/" + userId + "/full.jpg";
        VerifiedUpload upload = new VerifiedUpload(key, "version-1", "\"etag-1\"", 123L, "image/jpeg");
        Store store = store(42L, "한스푼");
        when(s3StorageService.resolveKey(userId, key)).thenReturn(key);
        when(storeRepository.findByIdAndStatus(42L, StoreStatus.ACTIVE)).thenReturn(Optional.of(store));
        when(s3StorageService.verifyUploadObject(key)).thenReturn(upload);
        when(scanSessionRepository.saveAndFlush(any(ScanSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, ScanSession.class));
        doThrow(new TaskRejectedException("full")).when(scanProcessor).process(any(), any(), any(), any());

        assertThatThrownBy(() -> scanService.startScan(
                        userId, new StartScanRequest(key, "upload", 42L, StoreMatchMethod.GPS_CANDIDATE)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.SCAN_CAPACITY_EXCEEDED);

        verify(scanStateWriter).deleteRejected(any());
    }

    @Test
    void getScanThrowsWhenNotOwnedOrMissing() {
        UUID userId = UUID.randomUUID();
        UUID scanId = UUID.randomUUID();
        when(scanSessionRepository.findByIdAndUserId(scanId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scanService.getScan(userId, scanId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SCAN_NOT_FOUND);
    }

    @Test
    void getScanReturnsResultForOwner() {
        UUID userId = UUID.randomUUID();
        ScanSession session = ScanSession.start(
                userId, "scans/" + userId + "/completed.jpg", 42L, "한스푼", StoreMatchMethod.GPS_CANDIDATE);
        session.changeTitle("custom title");
        session.applyPrepareResult(2, Instant.now(), "ko");
        session.applyJudgeResult(List.of(RiskLevel.DANGER, RiskLevel.SAFE), null);
        UUID scanId = session.getId();
        when(scanSessionRepository.findByIdAndUserId(scanId, userId)).thenReturn(Optional.of(session));
        when(menuAnalysisRepository.findByScanSessionIdOrderByDisplayOrder(scanId))
                .thenReturn(List.of());
        ScanRecordResponse savedRecord = new ScanRecordResponse(
                scanId, new ScanStoreSummary(42L, "한스푼"), true, StoreMatchMethod.GPS_CANDIDATE, Instant.now());
        when(scanRecordService.findResponse(session)).thenReturn(savedRecord);

        var response = scanService.getScan(userId, scanId);

        assertThat(response.scanId()).isEqualTo(scanId);
        assertThat(response.status()).isEqualTo(ScanStatus.COMPLETED);
        assertThat(response.title()).isEqualTo("custom title");
        assertThat(response.store().storeId()).isEqualTo(42L);
        assertThat(response.store().name()).isEqualTo("한스푼");
        assertThat(response.record()).isEqualTo(savedRecord);
        assertThat(response.menuCount()).isEqualTo(2);
        assertThat(response.dangerCount()).isEqualTo(1);
        assertThat(response.safeCount()).isEqualTo(1);
        assertThat(response.menus()).isEmpty();
    }

    @Test
    void getScanAddsStoreMenuPhotoOnlyToMatchedMenus() {
        UUID userId = UUID.randomUUID();
        ScanSession session = ScanSession.start(userId, "scans/photo.jpg", 10L, "기와", StoreMatchMethod.NAME_SEARCH);
        UUID scanId = session.getId();
        MenuAnalysis withPhoto = MenuAnalysis.prepared(scanId, 0, "김치찌개", null, null, null, null, 10L, 1L);
        MenuAnalysis withoutPhoto = MenuAnalysis.prepared(scanId, 1, "공기밥", null, null, null, null, 10L, null);
        when(scanSessionRepository.findByIdAndUserId(scanId, userId)).thenReturn(Optional.of(session));
        when(menuAnalysisRepository.findByScanSessionIdOrderByDisplayOrder(scanId))
                .thenReturn(List.of(withPhoto, withoutPhoto));
        when(menuPhotoResolver.find(scanId))
                .thenReturn(
                        Map.of(withPhoto.getId(), new MenuPhoto("https://cdn.test/menus/stores/a.jpg", "사진 제공: 기와")));

        var response = scanService.getScan(userId, scanId);

        assertThat(response.menus())
                .extracting("imageUrl", "imageAttribution")
                .containsExactly(tuple("https://cdn.test/menus/stores/a.jpg", "사진 제공: 기와"), tuple(null, null));
    }

    @Test
    void getScanReturnsMenuNamesAndTranslatedName() {
        UUID userId = UUID.randomUUID();
        ScanSession session = ScanSession.create(userId, null, null, ScanStatus.PROCESSING, null);
        session.applyPrepareResult(1, Instant.now(), "en");
        UUID scanId = session.getId();
        MenuAnalysis menu =
                MenuAnalysis.prepared(scanId, 0, "파돈불고기", "파돈불고기 200g과 된장찌개", "14,000", null, null, null, null);
        when(scanSessionRepository.findByIdAndUserId(scanId, userId)).thenReturn(Optional.of(session));
        when(menuAnalysisRepository.findByScanSessionIdOrderByDisplayOrder(scanId))
                .thenReturn(List.of(menu));
        when(menuAnalysisRepository.findTranslatedNames(scanId, "en")).thenReturn(List.of(translation(menu.getId())));

        var response = scanService.getScan(userId, scanId);

        assertThat(response.menus()).singleElement().satisfies(result -> {
            assertThat(result.displayName()).isEqualTo("파돈불고기");
            assertThat(result.translatedName()).isEqualTo("Green Onion Bulgogi");
            assertThat(result.description()).isEqualTo("파돈불고기 200g과 된장찌개");
            assertThat(result.translatedDescription()).isNull();
        });
    }

    @Test
    void getScanGroupsStaffQuestionsByMenu() {
        UUID userId = UUID.randomUUID();
        ScanSession session = ScanSession.start(userId, "scans/q.jpg", 10L, "기와", StoreMatchMethod.NAME_SEARCH);
        UUID scanId = session.getId();
        MenuAnalysis asked = MenuAnalysis.prepared(scanId, 0, "김치찌개", null, null, null, null, 10L, 1L);
        MenuAnalysis notAsked = MenuAnalysis.prepared(scanId, 1, "공기밥", null, null, null, null, 10L, 2L);
        StaffQuestionResult question = new StaffQuestionResult(
                UUID.randomUUID(),
                "card-1",
                "flag",
                null,
                "has_unclear_jeotgal",
                List.of("is_shrimp"),
                Map.of("ko", "질문"),
                "pending",
                null,
                null);
        when(scanSessionRepository.findByIdAndUserId(scanId, userId)).thenReturn(Optional.of(session));
        when(menuAnalysisRepository.findByScanSessionIdOrderByDisplayOrder(scanId))
                .thenReturn(List.of(asked, notAsked));
        when(staffQuestionRepository.findByScan(scanId, null))
                .thenReturn(List.of(new MenuStaffQuestion(asked.getId(), question)));

        var response = scanService.getScan(userId, scanId);

        assertThat(response.menus().get(0).staffQuestions()).containsExactly(question);
        assertThat(response.menus().get(1).staffQuestions()).isEmpty();
    }

    @Test
    void getScanSkipsStaffQuestionsWithoutStore() {
        UUID userId = UUID.randomUUID();
        ScanSession session = ScanSession.create(userId, null, null, ScanStatus.PROCESSING, null);
        UUID scanId = session.getId();
        MenuAnalysis menu = MenuAnalysis.prepared(scanId, 0, "김치찌개", null, null, null, null, null, null);
        when(scanSessionRepository.findByIdAndUserId(scanId, userId)).thenReturn(Optional.of(session));
        when(menuAnalysisRepository.findByScanSessionIdOrderByDisplayOrder(scanId))
                .thenReturn(List.of(menu));

        var response = scanService.getScan(userId, scanId);

        assertThat(response.menus().get(0).staffQuestions()).isEmpty();
        verify(staffQuestionRepository, never()).findByScan(any(), any());
    }

    private static MenuTranslationProjection translation(UUID menuAnalysisId) {
        return new MenuTranslationProjection() {
            @Override
            public UUID getMenuAnalysisId() {
                return menuAnalysisId;
            }

            @Override
            public String getTranslatedName() {
                return "Green Onion Bulgogi";
            }
        };
    }

    @Test
    void getScanReturnsFailureCodeWithoutInternalExceptionDetails() {
        UUID userId = UUID.randomUUID();
        ScanSession session = ScanSession.startLegacy(userId, "scans/" + userId + "/failed.jpg");
        session.markFailed(ErrorCode.AI_SERVICE_OVERLOADED.getCode());
        when(scanSessionRepository.findByIdAndUserId(session.getId(), userId)).thenReturn(Optional.of(session));
        when(menuAnalysisRepository.findByScanSessionIdOrderByDisplayOrder(session.getId()))
                .thenReturn(List.of());

        var response = scanService.getScan(userId, session.getId());

        assertThat(response.status()).isEqualTo(ScanStatus.FAILED);
        assertThat(response.failureCode()).isEqualTo("AI_SERVICE_OVERLOADED");
    }

    @Test
    void getScanReturnsRetakeReasonsAndSuggestions() {
        UUID userId = UUID.randomUUID();
        ScanSession session = ScanSession.startLegacy(userId, "scans/" + userId + "/blurred.jpg");
        session.applyNeedsRetake(List.of("이미지가 흐려 메뉴판 판독이 어렵습니다."), List.of("카메라의 초점을 맞춰 다시 촬영해 주세요."));
        when(scanSessionRepository.findByIdAndUserId(session.getId(), userId)).thenReturn(Optional.of(session));
        when(menuAnalysisRepository.findByScanSessionIdOrderByDisplayOrder(session.getId()))
                .thenReturn(List.of());

        var response = scanService.getScan(userId, session.getId());

        assertThat(response.status()).isEqualTo(ScanStatus.NEEDS_RETAKE);
        assertThat(response.retakeReasons()).containsExactly("이미지가 흐려 메뉴판 판독이 어렵습니다.");
        assertThat(response.retakeSuggestions()).containsExactly("카메라의 초점을 맞춰 다시 촬영해 주세요.");
    }

    @Test
    void getScanReturnsNullTitleWhenNotEditedAndRawScannedAt() {
        UUID userId = UUID.randomUUID();
        Instant scannedAt = Instant.parse("2026-06-06T12:00:00Z");
        // title 미수정 → null. 기본 제목 현지화는 FE 가 scannedAt 으로 처리(백엔드는 포맷 안 함)
        ScanSession session = ScanSession.create(userId, null, 1, ScanStatus.COMPLETED, scannedAt);
        UUID scanId = session.getId();
        when(scanSessionRepository.findByIdAndUserId(scanId, userId)).thenReturn(Optional.of(session));
        when(menuAnalysisRepository.findByScanSessionIdOrderByDisplayOrder(scanId))
                .thenReturn(List.of());

        var response = scanService.getScan(userId, scanId);

        assertThat(response.title()).isNull();
        assertThat(response.scannedAt()).isEqualTo(scannedAt);
    }

    @Test
    void getScansReturnsRiskCountsAndAttachedStore() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        ScanSession session = ScanSession.startWithoutStore(userId, "scans/" + userId + "/list.jpg");
        session.applyPrepareResult(3, Instant.now(), "ko");
        session.applyJudgeResult(List.of(RiskLevel.DANGER, RiskLevel.CAUTION, RiskLevel.SAFE), null);
        ScanRecord record =
                ScanRecord.create(session.getId(), 77L, "연결한 식당", StoreMatchMethod.NAME_SEARCH, Instant.now());
        when(scanSessionRepository.findByUserIdAndScanStatus(userId, ScanStatus.COMPLETED, pageable))
                .thenReturn(new PageImpl<>(List.of(session), pageable, 1));
        when(scanRecordService.findRecords(List.of(session.getId()))).thenReturn(Map.of(session.getId(), record));

        PageResponse<ScanHistoryItem> response = scanService.getScans(userId, pageable);

        assertThat(response.items()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
        ScanHistoryItem item = response.items().get(0);
        assertThat(item.scanId()).isEqualTo(session.getId());
        assertThat(item.status()).isEqualTo(ScanStatus.COMPLETED);
        assertThat(item.store().storeId()).isEqualTo(77L);
        assertThat(item.store().name()).isEqualTo("연결한 식당");
        assertThat(item.dangerCount()).isEqualTo(1);
        assertThat(item.cautionCount()).isEqualTo(1);
    }

    @Test
    void getScansKeepsLockedStore() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        ScanSession session = ScanSession.start(
                userId, "scans/" + userId + "/locked.jpg", 42L, "한스푼", StoreMatchMethod.GPS_CANDIDATE);
        session.applyPrepareResult(2, Instant.now(), "ko");
        session.applyJudgeResult(List.of(RiskLevel.CAUTION), null);
        when(scanSessionRepository.findByUserIdAndScanStatus(userId, ScanStatus.COMPLETED, pageable))
                .thenReturn(new PageImpl<>(List.of(session), pageable, 1));

        ScanHistoryItem item = scanService.getScans(userId, pageable).items().get(0);

        assertThat(item.store().storeId()).isEqualTo(42L);
        assertThat(item.dangerCount()).isZero();
        assertThat(item.cautionCount()).isEqualTo(1);
    }

    @Test
    void updateTitleChangesTitleForOwner() {
        UUID userId = UUID.randomUUID();
        ScanSession session = ScanSession.create(userId, null, 2, ScanStatus.COMPLETED, Instant.now());
        UUID scanId = session.getId();
        when(scanSessionRepository.findByIdAndUserId(scanId, userId)).thenReturn(Optional.of(session));

        ScanHistoryItem item = scanService.updateTitle(userId, scanId, new UpdateScanTitleRequest("강남 삼겹살집"));

        assertThat(item.title()).isEqualTo("강남 삼겹살집");
        assertThat(session.getTitle()).isEqualTo("강남 삼겹살집");
    }

    @Test
    void updateTitleThrowsWhenNotOwnedOrMissing() {
        UUID userId = UUID.randomUUID();
        UUID scanId = UUID.randomUUID();
        when(scanSessionRepository.findByIdAndUserId(scanId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scanService.updateTitle(userId, scanId, new UpdateScanTitleRequest("x")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SCAN_NOT_FOUND);
    }

    @Test
    void deleteScanDeletesForOwner() {
        UUID userId = UUID.randomUUID();
        ScanSession session = ScanSession.create(userId, "menu.jpg", 1, ScanStatus.COMPLETED, Instant.now());
        UUID scanId = session.getId();
        when(scanSessionRepository.findByIdAndUserId(scanId, userId)).thenReturn(Optional.of(session));

        scanService.deleteScan(userId, scanId);

        verify(scanSessionRepository).delete(session);
    }

    @Test
    void deleteScanThrowsWhenNotOwnedOrMissing() {
        UUID userId = UUID.randomUUID();
        UUID scanId = UUID.randomUUID();
        when(scanSessionRepository.findByIdAndUserId(scanId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scanService.deleteScan(userId, scanId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SCAN_NOT_FOUND);
        verify(scanSessionRepository, never()).delete(any());
    }

    private Store store(Long id, String name) {
        Store store = mock(Store.class);
        when(store.getId()).thenReturn(id);
        when(store.getName()).thenReturn(name);
        return store;
    }
}
