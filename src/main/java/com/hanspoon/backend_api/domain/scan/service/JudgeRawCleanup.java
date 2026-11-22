package com.hanspoon.backend_api.domain.scan.service;

import com.hanspoon.backend_api.domain.scan.repository.ScanAiResultRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** scan_ai_results.judge_raw 판정 후 90일 보관 뒤 NULL. 여러 인스턴스가 함께 돌아도 결과 동일. */
@Component
public class JudgeRawCleanup {

    static final Duration RETENTION = Duration.ofDays(90);

    private static final Logger log = LoggerFactory.getLogger(JudgeRawCleanup.class);

    private final ScanAiResultRepository scanAiResultRepository;
    private final Clock clock;

    @Autowired
    public JudgeRawCleanup(ScanAiResultRepository scanAiResultRepository) {
        this(scanAiResultRepository, Clock.systemUTC());
    }

    JudgeRawCleanup(ScanAiResultRepository scanAiResultRepository, Clock clock) {
        this.scanAiResultRepository = scanAiResultRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.judge-raw-cleanup.cron:0 30 4 * * *}", zone = "Asia/Seoul")
    @Transactional
    public void clean() {
        Instant now = clock.instant();
        int cleared = scanAiResultRepository.clearJudgeRawBefore(now.minus(RETENTION), now);
        if (cleared > 0) {
            log.info("Cleared judge_raw of {} scans older than {} days", cleared, RETENTION.toDays());
        }
    }
}
