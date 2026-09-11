package com.hanspoon.backend_api.domain.scan.service;

import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 서버 재시작 등으로 인메모리 비동기 작업이 유실된 PROCESSING 세션을 RDB 기준으로 회수한다. */
@Component
public class StaleScanRecovery {

    private static final Logger log = LoggerFactory.getLogger(StaleScanRecovery.class);

    private final ScanSessionRepository scanSessionRepository;
    private final Duration staleAfter;
    private final Clock clock;

    @Autowired
    public StaleScanRecovery(
            ScanSessionRepository scanSessionRepository,
            @Value("${app.scan-recovery.stale-after:2m}") Duration staleAfter) {
        this(scanSessionRepository, staleAfter, Clock.systemUTC());
    }

    StaleScanRecovery(ScanSessionRepository scanSessionRepository, Duration staleAfter, Clock clock) {
        this.scanSessionRepository = scanSessionRepository;
        this.staleAfter = staleAfter;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.scan-recovery.interval-ms:60000}")
    @Transactional
    public void recover() {
        Instant now = clock.instant();
        int recovered = scanSessionRepository.markStaleProcessingAsFailed(
                ScanStatus.PROCESSING,
                ScanStatus.FAILED,
                ErrorCode.SCAN_PROCESSING_TIMEOUT.getCode(),
                now.minus(staleAfter),
                now);
        if (recovered > 0) {
            log.warn("Recovered {} stale scan sessions", recovered);
        }
    }
}
