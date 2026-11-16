package com.hanspoon.backend_api.domain.scan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hanspoon.backend_api.TestcontainersConfiguration;
import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.domain.scan.entity.MenuAnalysis;
import com.hanspoon.backend_api.domain.scan.entity.ScanImage;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import com.hanspoon.backend_api.domain.scan.repository.ScanImageRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.user.entity.User;
import com.hanspoon.backend_api.domain.user.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ScanStateWriterIntegrationTest {

    @Autowired
    private ScanStateWriter scanStateWriter;

    @Autowired
    private ScanSessionRepository scanSessionRepository;

    @Autowired
    private ScanImageRepository scanImageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StaleScanRecovery staleScanRecovery;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void commitsOcrResultAndFailureInIndependentTransactions() {
        User user = userRepository.save(User.create(uniqueEmail("tx"), "tx-user", "ko"));
        String storageKey = "scans/" + user.getId() + "/tx.jpg";
        ScanSession session = scanSessionRepository.saveAndFlush(ScanSession.startLegacy(user.getId(), storageKey));

        scanStateWriter.applyOcrResult(
                session.getId(),
                ScanImage.create(
                        session.getId(),
                        "upload",
                        storageKey,
                        "s3://test/" + storageKey,
                        "image/jpeg",
                        123L,
                        "version-1",
                        "\"etag-1\""),
                2,
                Instant.parse("2026-09-11T00:00:00Z"));

        // 외부 후속 단계가 실패해도 앞선 OCR 트랜잭션은 유지되고 실패 상태는 별도 커밋된다.
        scanStateWriter.markFailed(session.getId(), "RULE_ENGINE_ERROR");

        ScanSession reloaded = scanSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(reloaded.getMenuCount()).isEqualTo(2);
        assertThat(reloaded.getScanStatus()).isEqualTo(ScanStatus.FAILED);
        assertThat(reloaded.getFailureCode()).isEqualTo("RULE_ENGINE_ERROR");
        assertThat(scanImageRepository.findByScanSessionId(session.getId())).isPresent();
    }

    @Test
    void completionStoresRiskCountersNextToLegacyRiskyMenuCount() {
        User user = userRepository.save(User.create(uniqueEmail("counts"), "count-user", "ko"));
        ScanSession session = scanSessionRepository.saveAndFlush(
                ScanSession.startLegacy(user.getId(), "scans/" + user.getId() + "/counts.jpg"));
        List<MenuAnalysis> analyses = List.of(
                analysis(session.getId(), 0, RiskLevel.DANGER),
                analysis(session.getId(), 1, RiskLevel.CAUTION),
                analysis(session.getId(), 2, RiskLevel.CAUTION),
                analysis(session.getId(), 3, null));

        scanStateWriter.complete(session.getId(), 3, analyses);

        ScanSession reloaded = scanSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(reloaded.getScanStatus()).isEqualTo(ScanStatus.COMPLETED);
        assertThat(reloaded.getRiskyMenuCount()).isEqualTo(3);
        assertThat(reloaded.getDangerCount()).isEqualTo(1);
        assertThat(reloaded.getCautionCount()).isEqualTo(2);
        assertThat(reloaded.getSafeCount()).isZero();
    }

    @Test
    void databaseConstraintRejectsDuplicateStorageKeyForTheSameUser() {
        User user = userRepository.save(User.create(uniqueEmail("idempotency"), "idempotent-user", "ko"));
        String storageKey = "scans/" + user.getId() + "/same.jpg";
        scanSessionRepository.saveAndFlush(ScanSession.startLegacy(user.getId(), storageKey));

        assertThatThrownBy(() -> scanSessionRepository.saveAndFlush(ScanSession.startLegacy(user.getId(), storageKey)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void recoversProcessingSessionLeftBehindByAStoppedWorker() {
        User user = userRepository.save(User.create(uniqueEmail("recovery"), "recovery-user", "ko"));
        String storageKey = "scans/" + user.getId() + "/stale.jpg";
        ScanSession session = scanSessionRepository.saveAndFlush(ScanSession.startLegacy(user.getId(), storageKey));
        jdbcTemplate.update(
                "update scan_sessions set updated_at = ? where id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofMinutes(3))),
                session.getId());

        staleScanRecovery.recover();

        ScanSession reloaded = scanSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(reloaded.getScanStatus()).isEqualTo(ScanStatus.FAILED);
        assertThat(reloaded.getFailureCode()).isEqualTo("SCAN_PROCESSING_TIMEOUT");
    }

    private static MenuAnalysis analysis(UUID scanId, int order, RiskLevel riskLevel) {
        return MenuAnalysis.create(
                scanId, order, "메뉴" + order, null, null, null, null, null, null, riskLevel, List.of(), null, null);
    }

    private String uniqueEmail(String prefix) {
        return prefix + "+" + UUID.randomUUID() + "@example.com";
    }
}
