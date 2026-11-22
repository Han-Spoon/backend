package com.hanspoon.backend_api.domain.scan.service;

import com.hanspoon.backend_api.domain.scan.repository.AnalyzeJudgeRepository;
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

/**
 * AI 원본 90일 보관 정리. judge_raw 는 판정 기준 NULL, 적용 완료 명령은 수신 기준 삭제.
 * 여러 인스턴스가 함께 돌아도 결과 동일.
 */
@Component
public class AiRetentionCleanup {

    static final Duration RETENTION = Duration.ofDays(90);

    private static final Logger log = LoggerFactory.getLogger(AiRetentionCleanup.class);

    private final ScanAiResultRepository scanAiResultRepository;
    private final AnalyzeJudgeRepository analyzeJudgeRepository;
    private final Clock clock;

    @Autowired
    public AiRetentionCleanup(
            ScanAiResultRepository scanAiResultRepository, AnalyzeJudgeRepository analyzeJudgeRepository) {
        this(scanAiResultRepository, analyzeJudgeRepository, Clock.systemUTC());
    }

    AiRetentionCleanup(
            ScanAiResultRepository scanAiResultRepository, AnalyzeJudgeRepository analyzeJudgeRepository, Clock clock) {
        this.scanAiResultRepository = scanAiResultRepository;
        this.analyzeJudgeRepository = analyzeJudgeRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.ai-retention-cleanup.cron:0 30 4 * * *}", zone = "Asia/Seoul")
    @Transactional
    public void clean() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(RETENTION);
        int cleared = scanAiResultRepository.clearJudgeRawBefore(cutoff, now);
        int deleted = analyzeJudgeRepository.deleteAppliedCommandsBefore(cutoff);
        if (cleared > 0 || deleted > 0) {
            log.info(
                    "AI retention cleanup: judge_raw cleared={}, applied commands deleted={} (older than {} days)",
                    cleared,
                    deleted,
                    RETENTION.toDays());
        }
    }
}
