package com.hanspoon.backend_api.domain.scan.service;

import com.hanspoon.backend_api.domain.scan.entity.MenuAnalysis;
import com.hanspoon.backend_api.domain.scan.entity.MenuImage;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import com.hanspoon.backend_api.domain.scan.repository.MenuAnalysisRepository;
import com.hanspoon.backend_api.domain.scan.repository.MenuImageRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스캔 상태 전이를 짧은 트랜잭션 단위로 커밋하는 쓰기 전담 컴포넌트.
 *
 * <p>외부 I/O 추가 금지. 트랜젝션의 DB 커넥션이 점유 방지.
 * <p>세션은 트랜잭션마다 재조회.
 */
@Component
public class ScanStateWriter {

    private static final Logger log = LoggerFactory.getLogger(ScanStateWriter.class);

    private final ScanSessionRepository scanSessionRepository;
    private final MenuImageRepository menuImageRepository;
    private final MenuAnalysisRepository menuAnalysisRepository;

    public ScanStateWriter(
            ScanSessionRepository scanSessionRepository,
            MenuImageRepository menuImageRepository,
            MenuAnalysisRepository menuAnalysisRepository) {
        this.scanSessionRepository = scanSessionRepository;
        this.menuImageRepository = menuImageRepository;
        this.menuAnalysisRepository = menuAnalysisRepository;
    }

    @Transactional
    public void applyOcrResult(UUID scanId, MenuImage menuImage, Integer menuCount, Instant scannedAt) {
        menuImageRepository.save(menuImage);
        session(scanId).ifPresent(session -> session.applyOcrResult(menuCount, scannedAt));
    }

    @Transactional
    public void applyNeedsRetake(UUID scanId, List<String> retakeReasons, List<String> retakeSuggestions) {
        session(scanId).ifPresent(session -> session.applyNeedsRetake(retakeReasons, retakeSuggestions));
    }

    @Transactional
    public void complete(UUID scanId, Integer riskyMenuCount, List<MenuAnalysis> analyses) {
        menuAnalysisRepository.saveAll(analyses);
        session(scanId).ifPresent(session -> session.applyRuleEngineResult(riskyMenuCount, ScanStatus.COMPLETED));
    }

    @Transactional
    public void markFailed(UUID scanId, String failureCode) {
        session(scanId).ifPresent(session -> session.markFailed(failureCode));
    }

    /** 실행 풀에서 수락되지 않은 작업은 사용자에게 scanId를 반환하기 전에 제거한다. */
    @Transactional
    public void deleteRejected(UUID scanId) {
        scanSessionRepository.deleteById(scanId);
    }

    private Optional<ScanSession> session(UUID scanId) {
        Optional<ScanSession> found = scanSessionRepository.findById(scanId);
        if (found.isEmpty()) {
            log.warn("Scan session not found while writing state: {}", scanId);
        }
        return found;
    }
}
