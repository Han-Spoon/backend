package com.hanspoon.backend_api.domain.scan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hanspoon.backend_api.TestcontainersConfiguration;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
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
    private ScanSessionRepository scanSessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StaleScanRecovery staleScanRecovery;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    private String uniqueEmail(String prefix) {
        return prefix + "+" + UUID.randomUUID() + "@example.com";
    }
}
