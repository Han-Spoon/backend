package com.hanspoon.backend_api.domain.scan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hanspoon.backend_api.TestcontainersConfiguration;
import com.hanspoon.backend_api.domain.scan.entity.MenuImage;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import com.hanspoon.backend_api.domain.scan.repository.MenuImageRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.user.entity.User;
import com.hanspoon.backend_api.domain.user.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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
    private MenuImageRepository menuImageRepository;

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
        ScanSession session = scanSessionRepository.saveAndFlush(ScanSession.start(user.getId(), storageKey));

        scanStateWriter.applyOcrResult(
                session.getId(),
                MenuImage.create(
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
        assertThat(menuImageRepository.findByScanSessionId(session.getId())).isPresent();
    }

    @Test
    void databaseConstraintRejectsDuplicateStorageKeyForTheSameUser() {
        User user = userRepository.save(User.create(uniqueEmail("idempotency"), "idempotent-user", "ko"));
        String storageKey = "scans/" + user.getId() + "/same.jpg";
        scanSessionRepository.saveAndFlush(ScanSession.start(user.getId(), storageKey));

        assertThatThrownBy(() -> scanSessionRepository.saveAndFlush(ScanSession.start(user.getId(), storageKey)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void recoversProcessingSessionLeftBehindByAStoppedWorker() {
        User user = userRepository.save(User.create(uniqueEmail("recovery"), "recovery-user", "ko"));
        String storageKey = "scans/" + user.getId() + "/stale.jpg";
        ScanSession session = scanSessionRepository.saveAndFlush(ScanSession.start(user.getId(), storageKey));
        jdbcTemplate.update(
                "update scan_sessions set updated_at = ? where id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofMinutes(3))),
                session.getId());

        staleScanRecovery.recover();

        ScanSession reloaded = scanSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(reloaded.getScanStatus()).isEqualTo(ScanStatus.FAILED);
        assertThat(reloaded.getFailureCode()).isEqualTo("SCAN_PROCESSING_TIMEOUT");
    }

    private String uniqueEmail(String prefix) {
        return prefix + "+" + UUID.randomUUID() + "@example.com";
    }
}
