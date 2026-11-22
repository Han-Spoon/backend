package com.hanspoon.backend_api.domain.scan.service;

import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스캔 실패·거절 상태를 짧은 트랜잭션 단위로 커밋하는 쓰기 전담 컴포넌트.
 * 분석 결과 저장은 {@link AnalyzeStateWriter}.
 *
 * <p>외부 I/O 추가 금지. 트랜젝션의 DB 커넥션이 점유 방지.
 */
@Component
public class ScanStateWriter {

    private static final Logger log = LoggerFactory.getLogger(ScanStateWriter.class);

    private final ScanSessionRepository scanSessionRepository;

    public ScanStateWriter(ScanSessionRepository scanSessionRepository) {
        this.scanSessionRepository = scanSessionRepository;
    }

    @Transactional
    public void markFailed(UUID scanId, String failureCode) {
        scanSessionRepository
                .findById(scanId)
                .ifPresentOrElse(
                        session -> session.markFailed(failureCode),
                        () -> log.warn("Scan session not found while writing state: {}", scanId));
    }

    /** 실행 풀에서 수락되지 않은 작업은 사용자에게 scanId를 반환하기 전에 제거한다. */
    @Transactional
    public void deleteRejected(UUID scanId) {
        scanSessionRepository.deleteById(scanId);
    }
}
