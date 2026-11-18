package com.hanspoon.backend_api.domain.scan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hanspoon.backend_api.domain.scan.dto.SaveScanRecordRequest;
import com.hanspoon.backend_api.domain.scan.entity.ScanRecord;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import com.hanspoon.backend_api.domain.scan.repository.ScanRecordRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.store.entity.Store;
import com.hanspoon.backend_api.domain.store.entity.StoreMatchMethod;
import com.hanspoon.backend_api.domain.store.entity.StoreStatus;
import com.hanspoon.backend_api.domain.store.repository.StoreRepository;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScanRecordServiceTest {

    @Mock
    private ScanSessionRepository scanSessionRepository;

    @Mock
    private ScanRecordRepository scanRecordRepository;

    @Mock
    private StoreRepository storeRepository;

    @InjectMocks
    private ScanRecordService scanRecordService;

    @Test
    void savesLockedScanStoreWithoutDuplicatingItAsAttachment() {
        UUID userId = UUID.randomUUID();
        ScanSession session = completedSessionWithStore(userId, 42L, "한스푼");
        when(scanSessionRepository.findByIdAndUserIdForUpdate(session.getId(), userId))
                .thenReturn(Optional.of(session));
        when(scanRecordRepository.findById(session.getId())).thenReturn(Optional.empty());
        when(scanRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = scanRecordService.save(userId, session.getId(), new SaveScanRecordRequest(null, null));

        assertThat(response.storeLocked()).isTrue();
        assertThat(response.store().storeId()).isEqualTo(42L);
        ArgumentCaptor<ScanRecord> captor = ArgumentCaptor.forClass(ScanRecord.class);
        verify(scanRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getAttachedStoreId()).isNull();
    }

    @Test
    void attachesActiveStoreOnlyWhenScanStartedWithoutStore() {
        UUID userId = UUID.randomUUID();
        ScanSession session = completedSessionWithoutStore(userId);
        Store store = store(77L, "나중에 고른 식당");
        when(scanSessionRepository.findByIdAndUserIdForUpdate(session.getId(), userId))
                .thenReturn(Optional.of(session));
        when(storeRepository.findByIdAndStatus(77L, StoreStatus.ACTIVE)).thenReturn(Optional.of(store));
        when(scanRecordRepository.findById(session.getId())).thenReturn(Optional.empty());
        when(scanRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = scanRecordService.save(
                userId, session.getId(), new SaveScanRecordRequest(77L, StoreMatchMethod.NAME_SEARCH));

        assertThat(response.storeLocked()).isFalse();
        assertThat(response.store().storeId()).isEqualTo(77L);
        assertThat(response.store().name()).isEqualTo("나중에 고른 식당");
        assertThat(response.storeMatchMethod()).isEqualTo(StoreMatchMethod.NAME_SEARCH);
        verify(storeRepository).findByIdAndStatus(77L, StoreStatus.ACTIVE);
    }

    @Test
    void rejectsStoreOverrideWhenScanContextIsLocked() {
        UUID userId = UUID.randomUUID();
        ScanSession session = completedSessionWithStore(userId, 42L, "원래 식당");
        when(scanSessionRepository.findByIdAndUserIdForUpdate(session.getId(), userId))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> scanRecordService.save(
                        userId, session.getId(), new SaveScanRecordRequest(77L, StoreMatchMethod.NAME_SEARCH)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.SCAN_STORE_CONTEXT_LOCKED);

        verify(storeRepository, never()).findByIdAndStatus(any(), any());
        verify(scanRecordRepository, never()).save(any());
    }

    @Test
    void emptyBodyDetachesPreviouslyAttachedStore() {
        UUID userId = UUID.randomUUID();
        ScanSession session = completedSessionWithoutStore(userId);
        ScanRecord existing = ScanRecord.create(
                session.getId(), 77L, "연결했던 식당", StoreMatchMethod.NAME_SEARCH, Instant.parse("2026-09-18T00:00:00Z"));
        when(scanSessionRepository.findByIdAndUserIdForUpdate(session.getId(), userId))
                .thenReturn(Optional.of(session));
        when(scanRecordRepository.findById(session.getId())).thenReturn(Optional.of(existing));

        var response = scanRecordService.save(userId, session.getId(), new SaveScanRecordRequest(null, null));

        assertThat(response.store()).isNull();
        assertThat(response.storeMatchMethod()).isNull();
        assertThat(existing.getAttachedStoreId()).isNull();
    }

    @Test
    void rejectsNonCompletedScan() {
        UUID userId = UUID.randomUUID();
        ScanSession session = ScanSession.startWithoutStore(userId, "scans/processing.jpg");
        when(scanSessionRepository.findByIdAndUserIdForUpdate(session.getId(), userId))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> scanRecordService.save(userId, session.getId(), new SaveScanRecordRequest(null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.SCAN_NOT_COMPLETED);
    }

    @Test
    void updatesExistingRecordIdempotently() {
        UUID userId = UUID.randomUUID();
        ScanSession session = completedSessionWithoutStore(userId);
        ScanRecord existing =
                ScanRecord.create(session.getId(), null, null, null, Instant.parse("2026-09-18T00:00:00Z"));
        when(scanSessionRepository.findByIdAndUserIdForUpdate(session.getId(), userId))
                .thenReturn(Optional.of(session));
        when(scanRecordRepository.findById(session.getId())).thenReturn(Optional.of(existing));

        var response = scanRecordService.save(userId, session.getId(), new SaveScanRecordRequest(null, null));

        assertThat(response.scanId()).isEqualTo(session.getId());
        verify(scanRecordRepository).save(existing);
    }

    @Test
    void preservesExistingAttachedStoreAfterItBecomesInactive() {
        UUID userId = UUID.randomUUID();
        ScanSession session = completedSessionWithoutStore(userId);
        ScanRecord existing = ScanRecord.create(
                session.getId(),
                77L,
                "지금은 비활성 식당",
                StoreMatchMethod.NAME_SEARCH,
                Instant.parse("2026-09-18T00:00:00Z"));
        when(scanSessionRepository.findByIdAndUserIdForUpdate(session.getId(), userId))
                .thenReturn(Optional.of(session));
        when(scanRecordRepository.findById(session.getId())).thenReturn(Optional.of(existing));

        var response = scanRecordService.save(
                userId, session.getId(), new SaveScanRecordRequest(77L, StoreMatchMethod.NAME_SEARCH));

        assertThat(response.store().name()).isEqualTo("지금은 비활성 식당");
        verify(storeRepository, never()).findByIdAndStatus(any(), any());
    }

    private ScanSession completedSessionWithStore(UUID userId, Long storeId, String storeName) {
        ScanSession session =
                ScanSession.start(userId, "scans/locked.jpg", storeId, storeName, StoreMatchMethod.GPS_CANDIDATE);
        session.applyOcrResult(1, Instant.now());
        session.applyRuleEngineResult(0, ScanStatus.COMPLETED);
        return session;
    }

    private ScanSession completedSessionWithoutStore(UUID userId) {
        ScanSession session = ScanSession.startWithoutStore(userId, "scans/unlocked.jpg");
        session.applyOcrResult(1, Instant.now());
        session.applyRuleEngineResult(0, ScanStatus.COMPLETED);
        return session;
    }

    private Store store(Long id, String name) {
        Store store = mock(Store.class);
        when(store.getId()).thenReturn(id);
        when(store.getName()).thenReturn(name);
        return store;
    }
}
