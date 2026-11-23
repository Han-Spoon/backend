package com.hanspoon.backend_api.domain.scan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hanspoon.backend_api.domain.scan.dto.ScanImageResponse;
import com.hanspoon.backend_api.domain.scan.entity.ScanImage;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.repository.ScanImageRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.upload.dto.ReadUrl;
import com.hanspoon.backend_api.domain.upload.service.S3StorageService;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ScanImageServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SCAN_ID = UUID.randomUUID();
    private static final Instant UPLOADED_AT = Instant.parse("2026-11-01T00:00:00Z");
    private static final String KEY = "scans/" + USER_ID + "/menu.jpg";

    private final ScanSessionRepository scanSessionRepository = mock(ScanSessionRepository.class);
    private final ScanImageRepository scanImageRepository = mock(ScanImageRepository.class);
    private final S3StorageService s3StorageService = mock(S3StorageService.class);

    private ScanImageService serviceAt(Instant now) {
        return new ScanImageService(
                scanSessionRepository, scanImageRepository, s3StorageService, Clock.fixed(now, ZoneOffset.UTC));
    }

    private void ownScanWithImage() {
        when(scanSessionRepository.findByIdAndUserId(SCAN_ID, USER_ID))
                .thenReturn(Optional.of(mock(ScanSession.class)));
        ScanImage image =
                ScanImage.create(SCAN_ID, "upload", KEY, "s3://bucket/" + KEY, "image/jpeg", 100L, "v1", "\"etag\"");
        ReflectionTestUtils.setField(image, "createdAt", UPLOADED_AT);
        when(scanImageRepository.findByScanSessionId(SCAN_ID)).thenReturn(Optional.of(image));
    }

    @Test
    void returnsReadUrlForVersionWithRetention() {
        ownScanWithImage();
        Instant urlExpiresAt = Instant.parse("2026-11-23T00:15:00Z");
        when(s3StorageService.createReadUrl(KEY, "v1")).thenReturn(new ReadUrl("https://signed", urlExpiresAt));

        ScanImageResponse response =
                serviceAt(Instant.parse("2026-11-23T00:00:00Z")).getImage(USER_ID, SCAN_ID);

        assertThat(response.imageUrl()).isEqualTo("https://signed");
        assertThat(response.contentType()).isEqualTo("image/jpeg");
        assertThat(response.expiresAt()).isEqualTo(urlExpiresAt);
        assertThat(response.availableUntil()).isEqualTo(Instant.parse("2027-01-30T00:00:00Z"));
    }

    @Test
    void returns410FromNinetyDaysAfterUpload() {
        ownScanWithImage();

        assertThatThrownBy(() ->
                        serviceAt(UPLOADED_AT.plus(ScanImageService.RETENTION)).getImage(USER_ID, SCAN_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SCAN_IMAGE_EXPIRED);
        verify(s3StorageService, never()).createReadUrl(any(), any());
    }

    @Test
    void returns404ForOtherUsersScan() {
        when(scanSessionRepository.findByIdAndUserId(SCAN_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceAt(UPLOADED_AT).getImage(USER_ID, SCAN_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SCAN_NOT_FOUND);
        verify(scanImageRepository, never()).findByScanSessionId(any());
    }

    @Test
    void returns404WhenImageRowMissing() {
        when(scanSessionRepository.findByIdAndUserId(SCAN_ID, USER_ID))
                .thenReturn(Optional.of(mock(ScanSession.class)));
        when(scanImageRepository.findByScanSessionId(SCAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceAt(UPLOADED_AT).getImage(USER_ID, SCAN_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SCAN_NOT_FOUND);
    }
}
