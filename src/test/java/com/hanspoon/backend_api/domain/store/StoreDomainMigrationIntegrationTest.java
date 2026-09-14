package com.hanspoon.backend_api.domain.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hanspoon.backend_api.TestcontainersConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Flyway V1~V5 전체 적용과 가게 도메인의 핵심 DB 계약을 실제 PostgreSQL에서 검증한다. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class StoreDomainMigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void installsSearchExtensionsAndPreservesUnicodeNormalizationContract() {
        List<String> extensions = jdbcTemplate.queryForList(
                "select extname from pg_extension where extname in ('cube', 'earthdistance', 'pg_trgm')", String.class);

        assertThat(extensions).containsExactlyInAnyOrder("cube", "earthdistance", "pg_trgm");
        assertThat(jdbcTemplate.queryForObject("select normalize_store_name(?)", String.class, "ＣＵ 마트 Ｂ１"))
                .isEqualTo("cu마트b1");
        assertThat(jdbcTemplate.queryForObject("select normalize_store_name(?)", String.class, "스시 さくら"))
                .isEqualTo("스시さくら");
    }

    @Test
    void usesBigintForStoreIdentifiers() {
        String storeIdType = jdbcTemplate.queryForObject(
                """
                select data_type
                  from information_schema.columns
                 where table_schema = 'public'
                   and table_name = 'stores'
                   and column_name = 'id'
                """,
                String.class);
        String scanStoreIdType = jdbcTemplate.queryForObject(
                """
                select data_type
                  from information_schema.columns
                 where table_schema = 'public'
                   and table_name = 'scan_sessions'
                   and column_name = 'store_id'
                """,
                String.class);

        assertThat(storeIdType).isEqualTo("bigint");
        assertThat(scanStoreIdType).isEqualTo("bigint");
    }

    @Test
    void rejectsStoreNameThatNormalizesToEmpty() {
        long categoryId = insertRootCategory();
        long batchId = insertCompletedBatch();

        assertThatThrownBy(() -> insertPublicStore(categoryId, batchId, "store-empty-name", "!!!"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsEmptyExternalStoreIdentifier() {
        long storeId = insertPublicStore(insertRootCategory(), insertCompletedBatch(), "store-external", "한그릇");

        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        insert into store_external_refs (store_id, provider, external_id, external_url)
                        values (?, 'kakao', '   ', '')
                        """,
                        storeId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsStoreWithoutCategory() {
        long batchId = insertCompletedBatch();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        insert into stores
                            (sbiz_store_no, name, lat, lng, origin, last_batch_id)
                        values ('missing-category', '분류없는식당', 37.5, 127.0, 'sbiz', ?)
                        """,
                        batchId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsRemovedUserSubmittedOrigin() {
        long categoryId = insertRootCategory();
        long batchId = insertCompletedBatch();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        insert into stores
                            (name, category_id, lat, lng, origin, last_batch_id)
                        values ('사용자제보식당', ?, 37.5, 127.0, 'user_submitted', ?)
                        """,
                        categoryId,
                        batchId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void preservesLegacyScanWithoutStoreContext() {
        UUID userId = insertUser("legacy-store-context@example.com");
        UUID scanId = UUID.randomUUID();

        int inserted = jdbcTemplate.update(
                "insert into scan_sessions (id, user_id, scan_status) values (?, ?, 'processing')", scanId, userId);

        assertThat(inserted).isOne();
    }

    @Test
    void rejectsPartialStoreContextOnScan() {
        long storeId = insertPublicStore(insertRootCategory(), insertCompletedBatch(), "store-scan", "스캔식당");
        UUID userId = insertUser("partial-store-context@example.com");

        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        insert into scan_sessions (id, user_id, scan_status, store_id)
                        values (?, ?, 'processing', ?)
                        """,
                        UUID.randomUUID(),
                        userId,
                        storeId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void acceptsCompleteStoreContextOnScan() {
        long storeId = insertPublicStore(insertRootCategory(), insertCompletedBatch(), "store-complete", "완전한식당");
        UUID userId = insertUser("complete-store-context@example.com");

        int inserted = jdbcTemplate.update(
                """
                insert into scan_sessions
                    (id, user_id, scan_status, store_id, store_name_snapshot, store_match_method)
                values (?, ?, 'processing', ?, '완전한식당', 'gps_candidate')
                """,
                UUID.randomUUID(),
                userId,
                storeId);

        assertThat(inserted).isOne();
    }

    @Test
    void enforcesImportBatchStatusAndFinishedAtPair() {
        assertThatThrownBy(
                        () -> jdbcTemplate.update(
                                """
                        insert into store_import_batches
                            (source, source_version, status, finished_at)
                        values ('sbiz', '209901', 'running', now())
                        """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long insertRootCategory() {
        return jdbcTemplate.queryForObject(
                "insert into store_categories (code, name, level) values ('I2', '음식', 1) returning id", Long.class);
    }

    private long insertCompletedBatch() {
        return jdbcTemplate.queryForObject(
                """
                insert into store_import_batches
                    (source, source_version, row_count, status, finished_at)
                values ('sbiz', '202606', 1, 'completed', now())
                returning id
                """,
                Long.class);
    }

    private long insertPublicStore(long categoryId, long batchId, String storeNo, String name) {
        return jdbcTemplate.queryForObject(
                """
                insert into stores
                    (sbiz_store_no, name, category_id, lat, lng, origin, last_batch_id)
                values (?, ?, ?, 37.5000, 127.0000, 'sbiz', ?)
                returning id
                """,
                Long.class,
                storeNo,
                name,
                categoryId,
                batchId);
    }

    private UUID insertUser(String email) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into users (id, email, nickname, language_code) values (?, ?, 'tester', 'ko')", userId, email);
        return userId;
    }
}
