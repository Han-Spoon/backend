package com.hanspoon.backend_api.domain.scan.service;

import com.hanspoon.backend_api.domain.scan.dto.ScanImageResponse;
import com.hanspoon.backend_api.domain.scan.entity.ScanImage;
import com.hanspoon.backend_api.domain.scan.repository.ScanImageRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.upload.dto.ReadUrl;
import com.hanspoon.backend_api.domain.upload.service.S3StorageService;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 원본 메뉴판 사진 조회. 보관 90일(S3 수명주기와 같음). */
@Service
public class ScanImageService {

    static final Duration RETENTION = Duration.ofDays(90);

    private final ScanSessionRepository scanSessionRepository;
    private final ScanImageRepository scanImageRepository;
    private final S3StorageService s3StorageService;
    private final Clock clock;

    @Autowired
    public ScanImageService(
            ScanSessionRepository scanSessionRepository,
            ScanImageRepository scanImageRepository,
            S3StorageService s3StorageService) {
        this(scanSessionRepository, scanImageRepository, s3StorageService, Clock.systemUTC());
    }

    ScanImageService(
            ScanSessionRepository scanSessionRepository,
            ScanImageRepository scanImageRepository,
            S3StorageService s3StorageService,
            Clock clock) {
        this.scanSessionRepository = scanSessionRepository;
        this.scanImageRepository = scanImageRepository;
        this.s3StorageService = s3StorageService;
        this.clock = clock;
    }

    /** 본인 스캔만. 사진 행이 없어도 SCAN_NOT_FOUND. */
    @Transactional(readOnly = true)
    public ScanImageResponse getImage(UUID userId, UUID scanId) {
        scanSessionRepository
                .findByIdAndUserId(scanId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCAN_NOT_FOUND));
        ScanImage image = scanImageRepository
                .findByScanSessionId(scanId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCAN_NOT_FOUND));

        Instant availableUntil = image.getCreatedAt().plus(RETENTION);
        if (!clock.instant().isBefore(availableUntil)) {
            throw new BusinessException(ErrorCode.SCAN_IMAGE_EXPIRED);
        }
        ReadUrl readUrl = s3StorageService.createReadUrl(image.getStorageKey(), image.getObjectVersionId());
        return new ScanImageResponse(readUrl.url(), image.getMimeType(), readUrl.expiresAt(), availableUntil);
    }
}
