package com.hanspoon.backend_api.domain.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hanspoon.backend_api.TestcontainersConfiguration;
import com.hanspoon.backend_api.domain.scan.entity.ScanFeedbackAnswer;
import com.hanspoon.backend_api.domain.scan.entity.ScanRecord;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import com.hanspoon.backend_api.domain.scan.repository.ScanRecordRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.store.entity.StoreMatchMethod;
import com.hanspoon.backend_api.domain.user.entity.User;
import com.hanspoon.backend_api.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ScanRecordPersistenceIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ScanSessionRepository scanSessionRepository;

    @Autowired
    private ScanRecordRepository scanRecordRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void persistsAttachedStoreSnapshotAndFeedbackAsOneToOneRecord() {
        User user = userRepository.save(User.create(uniqueEmail("record"), "record-user", "ko"));
        ScanSession scan = scanSessionRepository.save(
                ScanSession.create(user.getId(), null, 1, 0, ScanStatus.COMPLETED, Instant.now()));
        long storeId = insertStore("RECORD-STORE-1", "기록 식당");

        scanRecordRepository.save(ScanRecord.create(
                scan.getId(),
                storeId,
                "기록 식당",
                StoreMatchMethod.NAME_SEARCH,
                Map.of("allergy:shrimp", ScanFeedbackAnswer.YES),
                Instant.parse("2026-09-18T12:00:00Z")));
        entityManager.flush();
        entityManager.clear();

        ScanRecord reloaded = scanRecordRepository.findById(scan.getId()).orElseThrow();
        assertThat(reloaded.getAttachedStoreId()).isEqualTo(storeId);
        assertThat(reloaded.getAttachedStoreNameSnapshot()).isEqualTo("기록 식당");
        assertThat(reloaded.getAttachedStoreMatchMethod()).isEqualTo(StoreMatchMethod.NAME_SEARCH);
        assertThat(reloaded.getFeedback()).containsEntry("allergy:shrimp", ScanFeedbackAnswer.YES);
    }

    @Test
    void scanDeletionCascadesToRecord() {
        User user = userRepository.save(User.create(uniqueEmail("cascade"), "record-user", "ko"));
        ScanSession scan = scanSessionRepository.save(
                ScanSession.create(user.getId(), null, 1, 0, ScanStatus.COMPLETED, Instant.now()));
        scanRecordRepository.save(ScanRecord.create(scan.getId(), null, null, null, Map.of(), Instant.now()));
        entityManager.flush();
        entityManager.clear();

        scanSessionRepository.delete(
                scanSessionRepository.findById(scan.getId()).orElseThrow());
        entityManager.flush();
        entityManager.clear();

        assertThat(scanRecordRepository.findById(scan.getId())).isEmpty();
    }

    @Test
    void databaseRejectsPartialAttachedStoreContext() {
        User user = userRepository.save(User.create(uniqueEmail("partial"), "record-user", "ko"));
        ScanSession scan = scanSessionRepository.save(
                ScanSession.create(user.getId(), null, 1, 0, ScanStatus.COMPLETED, Instant.now()));
        long storeId = insertStore("RECORD-STORE-2", "불완전 기록 식당");
        entityManager.flush();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        insert into scan_records (scan_session_id, attached_store_id, feedback, saved_at)
                        values (?, ?, '{}'::jsonb, now())
                        """,
                        scan.getId(),
                        storeId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long insertStore(String storeNo, String name) {
        Long categoryId = jdbcTemplate.queryForObject(
                "insert into store_categories(code, name, level) values (?, '음식', 1) returning id",
                Long.class,
                "I" + UUID.randomUUID().toString().substring(0, 5));
        Long batchId = jdbcTemplate.queryForObject(
                """
                insert into store_import_batches(source, source_version)
                values ('sbiz', ?) returning id
                """,
                Long.class,
                String.valueOf(100000 + Math.abs(UUID.randomUUID().hashCode() % 899999)));
        return jdbcTemplate.queryForObject(
                """
                insert into stores(sbiz_store_no, name, category_id, lat, lng, origin, last_batch_id)
                values (?, ?, ?, 37.5, 127.0, 'sbiz', ?) returning id
                """,
                Long.class,
                storeNo,
                name,
                categoryId,
                batchId);
    }

    private String uniqueEmail(String prefix) {
        return prefix + "+" + UUID.randomUUID() + "@example.com";
    }
}
