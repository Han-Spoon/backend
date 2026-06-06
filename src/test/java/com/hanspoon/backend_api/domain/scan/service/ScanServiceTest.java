package com.hanspoon.backend_api.domain.scan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hanspoon.backend_api.domain.scan.dto.ScanCreatedResponse;
import com.hanspoon.backend_api.domain.scan.dto.ScanHistoryItem;
import com.hanspoon.backend_api.domain.scan.dto.StartScanRequest;
import com.hanspoon.backend_api.domain.scan.dto.UpdateScanTitleRequest;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import com.hanspoon.backend_api.domain.scan.repository.MenuAnalysisRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.upload.service.BlobStorageService;
import com.hanspoon.backend_api.global.common.PageResponse;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ScanServiceTest {

    @Mock
    private BlobStorageService blobStorageService;

    @Mock
    private ScanSessionRepository scanSessionRepository;

    @Mock
    private MenuAnalysisRepository menuAnalysisRepository;

    @Mock
    private ScanProcessor scanProcessor;

    @InjectMocks
    private ScanService scanService;

    @Test
    void startScanSavesSessionTriggersProcessorAndReturnsProcessing() {
        UUID userId = UUID.randomUUID();
        when(blobStorageService.extractStorageKey("menu-x.jpg")).thenReturn("menu-x.jpg");
        when(scanSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ScanCreatedResponse response = scanService.startScan(userId, new StartScanRequest("menu-x.jpg", "upload"));

        assertThat(response.status()).isEqualTo(ScanStatus.PROCESSING);
        assertThat(response.scanId()).isNotNull();
        verify(scanProcessor).process(eq(response.scanId()), eq(userId), eq("menu-x.jpg"), eq("upload"));
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
        ScanSession session = ScanSession.create(userId, "custom title", 2, 1, ScanStatus.COMPLETED, null);
        UUID scanId = session.getId();
        when(scanSessionRepository.findByIdAndUserId(scanId, userId)).thenReturn(Optional.of(session));
        when(menuAnalysisRepository.findByScanSessionIdOrderByDisplayOrder(scanId))
                .thenReturn(List.of());

        var response = scanService.getScan(userId, scanId);

        assertThat(response.scanId()).isEqualTo(scanId);
        assertThat(response.status()).isEqualTo(ScanStatus.COMPLETED);
        assertThat(response.title()).isEqualTo("custom title");
        assertThat(response.menuCount()).isEqualTo(2);
        assertThat(response.riskyMenuCount()).isEqualTo(1);
        assertThat(response.menus()).isEmpty();
    }

    @Test
    void getScanReturnsNullTitleWhenNotEditedAndRawScannedAt() {
        UUID userId = UUID.randomUUID();
        Instant scannedAt = Instant.parse("2026-06-06T12:00:00Z");
        // title 미수정 → null. 기본 제목 현지화는 FE 가 scannedAt 으로 처리(백엔드는 포맷 안 함)
        ScanSession session = ScanSession.create(userId, null, 1, 0, ScanStatus.COMPLETED, scannedAt);
        UUID scanId = session.getId();
        when(scanSessionRepository.findByIdAndUserId(scanId, userId)).thenReturn(Optional.of(session));
        when(menuAnalysisRepository.findByScanSessionIdOrderByDisplayOrder(scanId))
                .thenReturn(List.of());

        var response = scanService.getScan(userId, scanId);

        assertThat(response.title()).isNull();
        assertThat(response.scannedAt()).isEqualTo(scannedAt);
    }

    @Test
    void getScansReturnsMappedPage() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        ScanSession session = ScanSession.create(userId, "menu.jpg", 3, 1, ScanStatus.COMPLETED, Instant.now());
        when(scanSessionRepository.findByUserId(userId, pageable))
                .thenReturn(new PageImpl<>(List.of(session), pageable, 1));

        PageResponse<ScanHistoryItem> response = scanService.getScans(userId, pageable);

        assertThat(response.items()).hasSize(1);
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.items().get(0).scanId()).isEqualTo(session.getId());
        assertThat(response.items().get(0).riskyMenuCount()).isEqualTo(1);
    }

    @Test
    void updateTitleChangesTitleForOwner() {
        UUID userId = UUID.randomUUID();
        ScanSession session = ScanSession.create(userId, null, 2, 1, ScanStatus.COMPLETED, Instant.now());
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
        ScanSession session = ScanSession.create(userId, "menu.jpg", 1, 0, ScanStatus.COMPLETED, Instant.now());
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
}
