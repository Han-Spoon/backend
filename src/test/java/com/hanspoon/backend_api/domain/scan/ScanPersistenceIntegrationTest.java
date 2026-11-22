package com.hanspoon.backend_api.domain.scan;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanspoon.backend_api.TestcontainersConfiguration;
import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.domain.scan.entity.MenuAnalysis;
import com.hanspoon.backend_api.domain.scan.entity.ScanImage;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import com.hanspoon.backend_api.domain.scan.repository.MenuAnalysisRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanImageRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.store.entity.StoreMatchMethod;
import com.hanspoon.backend_api.domain.user.entity.User;
import com.hanspoon.backend_api.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * scan 도메인 영속화 + JSONB 라운드트립 검증. @SpringBootTest 부팅 자체가 Flyway V1(통합 베이스라인) 마이그레이트 후
 * Hibernate validate 를 수행하므로 스키마↔엔티티 정합도 함께 검증된다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ScanPersistenceIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ScanSessionRepository scanSessionRepository;

    @Autowired
    private ScanImageRepository scanImageRepository;

    @Autowired
    private MenuAnalysisRepository menuAnalysisRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void persistsAndReloadsAtomicStoreContext() {
        User user = userRepository.save(User.create("scan-store@example.com", "scanstore", "ko"));
        Long categoryId = jdbcTemplate.queryForObject(
                """
                insert into store_categories(code, name, level)
                values ('I2', '음식', 1)
                returning id
                """,
                Long.class);
        Long batchId = jdbcTemplate.queryForObject(
                """
                insert into store_import_batches(source, source_version)
                values ('sbiz', '202609')
                returning id
                """,
                Long.class);
        Long storeId = jdbcTemplate.queryForObject(
                """
                insert into stores(
                    sbiz_store_no, name, category_id, lat, lng, origin, last_batch_id
                ) values ('SCAN-STORE-1', '한스푼 강남점', ?, 37.4979, 127.0276, 'sbiz', ?)
                returning id
                """,
                Long.class,
                categoryId,
                batchId);

        ScanSession session = scanSessionRepository.save(ScanSession.start(
                user.getId(),
                "scans/" + user.getId() + "/store-context.jpg",
                storeId,
                "한스푼 강남점",
                StoreMatchMethod.NAME_SEARCH));

        entityManager.flush();
        entityManager.clear();

        ScanSession reloaded = scanSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(reloaded.getStoreId()).isEqualTo(storeId);
        assertThat(reloaded.getStoreNameSnapshot()).isEqualTo("한스푼 강남점");
        assertThat(reloaded.getStoreMatchMethod()).isEqualTo(StoreMatchMethod.NAME_SEARCH);
    }

    @Test
    void persistsAndReloadsScanGraphWithJsonbFields() {
        User user = userRepository.save(User.create("scan-test@example.com", "scanuser", "ko"));
        UUID userId = user.getId();

        ScanSession session = scanSessionRepository.save(
                ScanSession.create(userId, "menu_001", 2, ScanStatus.PROCESSING, Instant.now()));
        UUID sessionId = session.getId();

        scanImageRepository.save(ScanImage.create(
                sessionId,
                "upload",
                "scans/menu_001.jpg",
                "https://example.com/scans/menu_001.jpg",
                "image/jpeg",
                123456L,
                "version-1",
                "\"etag-1\""));

        MenuAnalysis prepared = menuAnalysisRepository.save(
                MenuAnalysis.prepared(sessionId, 0, "순두부", "", "8000", null, true, null, null));
        entityManager.flush();
        // 판정 컬럼은 Judge 저장(JDBC) 전용
        jdbcTemplate.update(
                """
                update menu_analyses
                   set risk_level = 'caution', ai_risk_level = 'caution',
                       matched_tags = '["is_shrimp","is_pork"]'::jsonb,
                       message = '{"verdict":"새우가 들어갈 수 있어요.","reason":null,"notes":[]}'::jsonb
                 where id = ?
                """,
                prepared.getId());
        entityManager.clear();

        ScanSession reloadedSession =
                scanSessionRepository.findByIdAndUserId(sessionId, userId).orElseThrow();
        assertThat(reloadedSession.getScanStatus()).isEqualTo(ScanStatus.PROCESSING);

        ScanImage reloadedImage =
                scanImageRepository.findByScanSessionId(sessionId).orElseThrow();
        assertThat(reloadedImage.getStorageKey()).isEqualTo("scans/menu_001.jpg");
        assertThat(reloadedImage.getSource()).isEqualTo("upload");
        assertThat(reloadedImage.getObjectVersionId()).isEqualTo("version-1");
        assertThat(reloadedImage.getETag()).isEqualTo("\"etag-1\"");

        List<MenuAnalysis> analyses = menuAnalysisRepository.findByScanSessionIdOrderByDisplayOrder(sessionId);
        assertThat(analyses).hasSize(1);
        MenuAnalysis analysis = analyses.get(0);
        assertThat(analysis.getDisplayName()).isEqualTo("순두부");
        assertThat(analysis.getIsSpicy()).isTrue();
        assertThat(analysis.getRiskLevel()).isEqualTo(RiskLevel.CAUTION);
        assertThat(analysis.getMatchedTags()).containsExactly("is_shrimp", "is_pork");
        assertThat(analysis.getMessage()).contains("새우가 들어갈 수 있어요.");
        assertThat(analysis.getStaffCards()).isEqualTo("[]");
    }

    @Test
    void appliesJudgeResultStoresCountersAndCompletes() {
        User user = userRepository.save(User.create("scan-update@example.com", "scanuser2", "ko"));
        ScanSession session = scanSessionRepository.save(
                ScanSession.create(user.getId(), "m", 3, ScanStatus.PROCESSING, Instant.now()));

        session.applyJudgeResult(List.of(RiskLevel.DANGER, RiskLevel.CAUTION, RiskLevel.CAUTION), null);
        scanSessionRepository.save(session);
        entityManager.flush();
        entityManager.clear();

        ScanSession reloaded = scanSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(reloaded.getScanStatus()).isEqualTo(ScanStatus.COMPLETED);
        assertThat(reloaded.getDangerCount()).isEqualTo(1);
        assertThat(reloaded.getCautionCount()).isEqualTo(2);
        assertThat(reloaded.getSafeCount()).isZero();
    }

    @Test
    void appliesNeedsRetakePersistsReasonsAsJsonb() {
        User user = userRepository.save(User.create("scan-retake@example.com", "scanuser3", "ko"));
        ScanSession session = scanSessionRepository.save(
                ScanSession.create(user.getId(), null, null, ScanStatus.PROCESSING, Instant.now()));

        session.applyNeedsRetake(List.of("too blurry", "low light"), List.of("카메라의 초점을 맞춰 다시 촬영해 주세요."));
        scanSessionRepository.save(session);
        entityManager.flush();
        entityManager.clear();

        ScanSession reloaded = scanSessionRepository.findById(session.getId()).orElseThrow();
        assertThat(reloaded.getScanStatus()).isEqualTo(ScanStatus.NEEDS_RETAKE);
        assertThat(reloaded.getRetakeReasons()).containsExactly("too blurry", "low light");
        assertThat(reloaded.getRetakeSuggestions()).containsExactly("카메라의 초점을 맞춰 다시 촬영해 주세요.");
    }

    @Test
    void deletingScanSessionCascadesToImageAndAnalyses() {
        User user = userRepository.save(User.create("scan-del@example.com", "scanuser4", "ko"));
        UUID userId = user.getId();
        ScanSession session =
                scanSessionRepository.save(ScanSession.create(userId, null, 1, ScanStatus.COMPLETED, Instant.now()));
        UUID sessionId = session.getId();
        scanImageRepository.save(
                ScanImage.create(sessionId, "upload", "scans/del.jpg", "u", "image/jpeg", 1L, null, null));
        menuAnalysisRepository.save(MenuAnalysis.prepared(sessionId, 0, "삼겹살", null, "9000", null, null, null, null));
        entityManager.flush();
        entityManager.clear();

        scanSessionRepository.delete(scanSessionRepository.findById(sessionId).orElseThrow());
        entityManager.flush();
        entityManager.clear();

        assertThat(scanSessionRepository.findById(sessionId)).isEmpty();
        assertThat(scanImageRepository.findByScanSessionId(sessionId)).isEmpty();
        assertThat(menuAnalysisRepository.findByScanSessionIdOrderByDisplayOrder(sessionId))
                .isEmpty();
    }
}
