package com.hanspoon.backend_api.domain.scan.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanspoon.backend_api.TestcontainersConfiguration;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.repository.ScanAiResultRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.user.entity.User;
import com.hanspoon.backend_api.domain.user.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class JudgeRawCleanupIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-22T00:00:00Z");

    @Autowired
    private ScanAiResultRepository scanAiResultRepository;

    @Autowired
    private ScanSessionRepository scanSessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void clearsOnlyJudgeRawPastRetention() {
        User user = userRepository.save(User.create("judge-raw@example.com", "judgeraw", "ko"));
        UUID expired = judgedScan(user.getId(), NOW.minus(Duration.ofDays(91)));
        UUID kept = judgedScan(user.getId(), NOW.minus(Duration.ofDays(89)));

        new JudgeRawCleanup(scanAiResultRepository, Clock.fixed(NOW, ZoneOffset.UTC)).clean();

        assertThat(rawAndEvidence(expired)).isEqualTo("null/array");
        assertThat(rawAndEvidence(kept)).isEqualTo("object/array");
    }

    private UUID judgedScan(UUID userId, Instant judgedAt) {
        ScanSession session = scanSessionRepository.saveAndFlush(
                ScanSession.startWithoutStore(userId, "scans/" + userId + "/" + UUID.randomUUID() + ".jpg"));
        jdbcTemplate.update(
                """
                insert into scan_ai_results (scan_session_id, prepare_id, schema_version, prepared_items_hash,
                                             prepared_items, prepare_signals, knowledge_revision, bundle_hash,
                                             judge_evidence, judge_warnings, judge_raw, judged_at)
                values (?, ?, '3.0', ?, '[]', '{}', 'rev-1', ?, '[]', '[]', '{"items":[]}', ?)
                """,
                session.getId(),
                UUID.randomUUID(),
                "sha256:" + "a".repeat(64),
                "sha256:" + "b".repeat(64),
                Timestamp.from(judgedAt));
        return session.getId();
    }

    private String rawAndEvidence(UUID scanId) {
        return jdbcTemplate.queryForObject(
                """
                select coalesce(jsonb_typeof(judge_raw), 'null') || '/' || jsonb_typeof(judge_evidence)
                from scan_ai_results where scan_session_id = ?
                """,
                String.class,
                scanId);
    }
}
