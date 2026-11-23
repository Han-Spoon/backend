package com.hanspoon.backend_api.domain.favorite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hanspoon.backend_api.TestcontainersConfiguration;
import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.domain.favorite.dto.SavedMenuResponse;
import com.hanspoon.backend_api.domain.scan.dto.ScanStoreSummary;
import com.hanspoon.backend_api.domain.scan.entity.MenuAnalysis;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.repository.MenuAnalysisRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.store.entity.StoreMatchMethod;
import com.hanspoon.backend_api.domain.user.entity.User;
import com.hanspoon.backend_api.domain.user.repository.UserRepository;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SavedMenuServiceIntegrationTest {

    @Autowired
    private SavedMenuService savedMenuService;

    @Autowired
    private ScanSessionRepository scanSessionRepository;

    @Autowired
    private MenuAnalysisRepository menuAnalysisRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private long storeId;
    private UUID menuId;

    /** 가게 스캔(en) 1건, 메뉴 1개(caution) + 정규 메뉴 영어명. */
    @BeforeEach
    void setUp() {
        userId = user("saver");
        storeId = insertStore();
        ScanSession session = ScanSession.start(
                userId, "scans/" + userId + "/menu.jpg", storeId, "한스푼", StoreMatchMethod.NAME_SEARCH);
        session.applyPrepareResult(1, Instant.now(), "en");
        session.applyJudgeResult(List.of(RiskLevel.CAUTION), null);
        UUID scanId = scanSessionRepository.saveAndFlush(session).getId();
        menuId = judgedMenu(scanId, "김치찌개", "caution");
        long canonicalId = jdbcTemplate.queryForObject(
                """
                insert into menus (name_ko, name_i18n, source, needs_review)
                values (?, '{"en":"Kimchi stew"}', 'curated', true) returning id
                """,
                Long.class,
                "김치찌개" + UUID.randomUUID().toString().substring(0, 6));
        jdbcTemplate.update(
                "update menu_analyses set menu_id = ?, menu_match_type = 'exact' where id = ?", canonicalId, menuId);
    }

    @Test
    void saveReturnsCurrentMenuValues() {
        SavedMenuResponse saved = savedMenuService.save(userId, menuId);

        assertThat(saved.scanMenuId()).isEqualTo(menuId);
        assertThat(saved.displayName()).isEqualTo("김치찌개");
        assertThat(saved.translatedName()).isEqualTo("Kimchi stew");
        assertThat(saved.riskLevel()).isEqualTo(RiskLevel.CAUTION);
        assertThat(saved.store()).isEqualTo(new ScanStoreSummary(storeId, "한스푼"));
        assertThat(saved.savedAt()).isNotNull();
    }

    @Test
    void saveTwiceKeepsOneRow() {
        SavedMenuResponse first = savedMenuService.save(userId, menuId);
        SavedMenuResponse second = savedMenuService.save(userId, menuId);

        assertThat(second).isEqualTo(first);
        assertThat(favoriteCount()).isEqualTo(1);
    }

    @Test
    void storelessScanUsesRecordStore() {
        ScanSession session = ScanSession.startWithoutStore(userId, "scans/" + userId + "/plain.jpg");
        session.applyPrepareResult(1, Instant.now(), "ko");
        session.applyJudgeResult(List.of(RiskLevel.CAUTION), null);
        UUID scanId = scanSessionRepository.saveAndFlush(session).getId();
        UUID plainMenu = judgedMenu(scanId, "잡채", "caution");

        assertThat(savedMenuService.save(userId, plainMenu).store()).isNull();

        jdbcTemplate.update(
                """
                insert into scan_records (scan_session_id, attached_store_id, attached_store_name_snapshot,
                                          attached_store_match_method, saved_at)
                values (?, ?, '한스푼', 'name_search', now())
                """,
                scanId,
                storeId);
        SavedMenuResponse saved = savedMenuService.save(userId, plainMenu);
        assertThat(saved.store()).isEqualTo(new ScanStoreSummary(storeId, "한스푼"));
        assertThat(saved.translatedName()).isNull();
    }

    @Test
    void otherUsersMenuIsNotFound() {
        assertThatThrownBy(() -> savedMenuService.save(user("other"), menuId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCAN_MENU_NOT_FOUND);
        assertThat(favoriteCount()).isZero();
    }

    @Test
    void unknownMenuIsNotFound() {
        assertThatThrownBy(() -> savedMenuService.save(userId, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCAN_MENU_NOT_FOUND);
    }

    @Test
    void deleteRemovesOnlyOnce() {
        savedMenuService.save(userId, menuId);

        savedMenuService.delete(userId, menuId);

        assertThat(favoriteCount()).isZero();
        assertThatThrownBy(() -> savedMenuService.delete(userId, menuId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SAVED_MENU_NOT_FOUND);
    }

    @Test
    void otherUserCannotDelete() {
        savedMenuService.save(userId, menuId);

        assertThatThrownBy(() -> savedMenuService.delete(user("thief"), menuId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SAVED_MENU_NOT_FOUND);
        assertThat(favoriteCount()).isEqualTo(1);
    }

    private int favoriteCount() {
        return jdbcTemplate.queryForObject(
                "select count(*) from favorite_menus where menu_analysis_id = ?", Integer.class, menuId);
    }

    private UUID user(String nickname) {
        return userRepository
                .save(User.create(nickname + "+" + UUID.randomUUID() + "@example.com", nickname, "en"))
                .getId();
    }

    private UUID judgedMenu(UUID scanId, String name, String risk) {
        MenuAnalysis menu = menuAnalysisRepository.saveAndFlush(
                MenuAnalysis.prepared(scanId, 0, name, null, null, null, null, null, null));
        jdbcTemplate.update(
                """
                update menu_analyses
                set risk_level = ?, ai_risk_level = ?, status = 'completed', evidence_basis = 'confirmed',
                    information_status = 'complete', staff_answer_status = 'none', display_order = 1
                where id = ?
                """,
                risk,
                risk,
                menu.getId());
        return menu.getId();
    }

    private long insertStore() {
        Long categoryId = jdbcTemplate.queryForObject(
                "insert into store_categories(code, name, level) values (?, '음식', 1) returning id",
                Long.class,
                "F" + UUID.randomUUID().toString().substring(0, 5));
        Long batchId = jdbcTemplate.queryForObject(
                "insert into store_import_batches(source, source_version) values ('sbiz', ?) returning id",
                Long.class,
                String.valueOf(100000 + Math.abs(UUID.randomUUID().hashCode() % 899999)));
        return jdbcTemplate.queryForObject(
                """
                insert into stores(sbiz_store_no, name, category_id, lat, lng, origin, last_batch_id)
                values (?, '한스푼', ?, 33.41, 126.51, 'sbiz', ?) returning id
                """,
                Long.class,
                "F" + UUID.randomUUID().toString().substring(0, 8),
                categoryId,
                batchId);
    }
}
