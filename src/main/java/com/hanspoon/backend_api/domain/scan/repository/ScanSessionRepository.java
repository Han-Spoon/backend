package com.hanspoon.backend_api.domain.scan.repository;

import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScanSessionRepository extends JpaRepository<ScanSession, UUID> {

    // 이력 목록은 분석이 끝난(completed) 스캔만 노출. processing/needs_retake/failed 는 단건 폴링용으로만 존재.
    Page<ScanSession> findByUserIdAndScanStatus(UUID userId, ScanStatus scanStatus, Pageable pageable);

    Optional<ScanSession> findByIdAndUserId(UUID id, UUID userId);

    Optional<ScanSession> findByUserIdAndStorageKey(UUID userId, String storageKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update ScanSession scan
               set scan.scanStatus = :failedStatus,
                   scan.failureCode = :failureCode,
                   scan.updatedAt = :recoveredAt,
                   scan.lockVersion = scan.lockVersion + 1
             where scan.scanStatus = :processingStatus
               and scan.updatedAt < :cutoff
            """)
    int markStaleProcessingAsFailed(
            @Param("processingStatus") ScanStatus processingStatus,
            @Param("failedStatus") ScanStatus failedStatus,
            @Param("failureCode") String failureCode,
            @Param("cutoff") Instant cutoff,
            @Param("recoveredAt") Instant recoveredAt);
}
