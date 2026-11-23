package com.hanspoon.backend_api.domain.scan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hanspoon.backend_api.TestcontainersConfiguration;
import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.domain.scan.dto.StaffAnswerRequest;
import com.hanspoon.backend_api.domain.scan.dto.StaffAnswerResponse;
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
import java.util.Map;
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
class StaffAnswerServiceIntegrationTest {

    private static final String HALAL =
            """
            {"religion_type":"halal","is_vegetarian":false,"vegetarian_type":null,"no_alcohol":false,
             "allergies":[],"no_spicy":false}""";

    @Autowired
    private StaffAnswerService staffAnswerService;

    @Autowired
    private ScanSessionRepository scanSessionRepository;

    @Autowired
    private MenuAnalysisRepository menuAnalysisRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;
    private UUID scanId;
    private UUID menuId;
    private UUID porkQuestion;
    private UUID shrimpQuestion;

    /** 가게 스캔 1건: 메뉴 2개(caution·safe), 제육에 재료(돼지)·플래그(새우) 질문. */
    @BeforeEach
    void setUp() {
        long storeId = insertStore();
        long storeMenuId = insertStoreMenu(storeId, "제육");
        long otherStoreMenuId = insertStoreMenu(storeId, "공기밥");
        long porkId = jdbcTemplate.queryForObject(
                "insert into ingredients (name_ko, tag_codes) values (?, '{is_pork}') returning id",
                Long.class,
                "돼지" + UUID.randomUUID().toString().substring(0, 8));
        User user = userRepository.save(User.create("staff+" + UUID.randomUUID() + "@example.com", "staff", "en"));
        userId = user.getId();
        ScanSession session = ScanSession.start(
                userId, "scans/" + userId + "/menu.jpg", storeId, "한스푼", StoreMatchMethod.NAME_SEARCH);
        session.applyPrepareResult(2, Instant.now(), "en");
        session.applyJudgeResult(List.of(RiskLevel.CAUTION, RiskLevel.SAFE), HALAL);
        scanId = scanSessionRepository.saveAndFlush(session).getId();
        menuId = judgedMenu(0, "제육", storeId, storeMenuId, "caution", "partial");
        judgedMenu(1, "공기밥", storeId, otherStoreMenuId, "safe", "complete");
        porkQuestion = question(storeId, storeMenuId, porkId, null, null, "card-pork");
        shrimpQuestion = question(storeId, storeMenuId, null, "has_unclear_jeotgal", "{is_shrimp}", "card-shrimp");
    }

    @Test
    void presentOverlappingDietRaisesMenuToDanger() {
        StaffAnswerResponse response = answer(porkQuestion, "present");

        assertThat(response.question().status()).isEqualTo("answered");
        assertThat(response.question().answer()).isEqualTo("present");
        assertThat(response.question().answeredAt()).isNotNull();
        assertThat(response.question().questionText()).isEqualTo(Map.of("ko", "질문", "en", "question"));
        assertThat(response.menu().scanMenuId()).isEqualTo(menuId);
        assertThat(response.menu().riskLevel()).isEqualTo(RiskLevel.DANGER);
        assertThat(response.menu().staffAnswerStatus()).isEqualTo("partial");
        assertThat(response.counts()).isEqualTo(new StaffAnswerResponse.Counts(1, 0, 1));
        assertThat(menuRow()).containsEntry("risk_level", "danger").containsEntry("ai_risk_level", "caution");
        assertThat(jdbcTemplate.queryForObject(
                        "select danger_count from scan_sessions where id = ?", Integer.class, scanId))
                .isEqualTo(1);
        assertThat(confirmation(porkQuestion))
                .containsEntry("present", true)
                .containsEntry("source", "staff_confirmed")
                .containsEntry("invalidated", false);
    }

    @Test
    void presentWithoutDietOverlapKeepsAiVerdict() {
        StaffAnswerResponse response = answer(shrimpQuestion, "present");

        assertThat(response.menu().riskLevel()).isEqualTo(RiskLevel.CAUTION);
        assertThat(jdbcTemplate.queryForObject(
                        """
                        select fc.present from staff_questions q
                        join flag_confirmations fc on fc.id = q.resolved_flag_confirmation_id where q.id = ?
                        """,
                        Boolean.class,
                        shrimpQuestion))
                .isTrue();
    }

    @Test
    void allAnsweredMakesStaffAnswerCompleteWithoutLoweringVerdict() {
        answer(porkQuestion, "absent");
        StaffAnswerResponse response = answer(shrimpQuestion, "absent");

        assertThat(response.menu().staffAnswerStatus()).isEqualTo("complete");
        assertThat(response.menu().riskLevel()).isEqualTo(RiskLevel.CAUTION);
        assertThat(confirmation(porkQuestion)).containsEntry("present", false);
    }

    @Test
    void correctingPresentRestoresAiRiskLevel() {
        answer(porkQuestion, "present");

        StaffAnswerResponse response = answer(porkQuestion, "absent");

        assertThat(response.question().answer()).isEqualTo("absent");
        assertThat(response.menu().riskLevel()).isEqualTo(RiskLevel.CAUTION);
        assertThat(response.counts()).isEqualTo(new StaffAnswerResponse.Counts(0, 1, 1));
        assertThat(confirmation(porkQuestion)).containsEntry("present", false);
    }

    @Test
    void unknownUnlinksAndInvalidatesConfirmation() {
        answer(porkQuestion, "present");
        Long confirmationId = jdbcTemplate.queryForObject(
                "select resolved_confirmation_id from staff_questions where id = ?", Long.class, porkQuestion);

        StaffAnswerResponse response = answer(porkQuestion, "unknown");

        assertThat(response.question().answer()).isEqualTo("unknown");
        assertThat(response.menu().riskLevel()).isEqualTo(RiskLevel.CAUTION);
        assertThat(response.menu().staffAnswerStatus()).isEqualTo("partial");
        assertThat(jdbcTemplate.queryForObject(
                        "select resolved_confirmation_id from staff_questions where id = ?", Long.class, porkQuestion))
                .isNull();
        assertThat(jdbcTemplate.queryForObject(
                        "select invalidated_at is not null from ingredient_confirmations where id = ?",
                        Boolean.class,
                        confirmationId))
                .isTrue();

        answer(porkQuestion, "present");
        assertThat(confirmation(porkQuestion)).containsEntry("invalidated", false);
    }

    @Test
    void otherUsersScanIsNotFound() {
        assertThatThrownBy(() -> staffAnswerService.answer(
                        UUID.randomUUID(), scanId, porkQuestion, new StaffAnswerRequest("present")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCAN_NOT_FOUND);
    }

    @Test
    void unknownQuestionIsNotFound() {
        assertThatThrownBy(() -> answer(UUID.randomUUID(), "present"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STAFF_QUESTION_NOT_FOUND);
    }

    @Test
    void expiredQuestionIsConflict() {
        jdbcTemplate.update("update staff_questions set status = 'expired' where id = ?", porkQuestion);

        assertThatThrownBy(() -> answer(porkQuestion, "present"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STAFF_QUESTION_EXPIRED);
    }

    @Test
    void storelessScanIsConflict() {
        ScanSession storeless = scanSessionRepository.saveAndFlush(
                ScanSession.startWithoutStore(userId, "scans/" + userId + "/" + UUID.randomUUID() + ".jpg"));

        assertThatThrownBy(() -> staffAnswerService.answer(
                        userId, storeless.getId(), porkQuestion, new StaffAnswerRequest("present")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.STAFF_QUESTION_REQUIRES_STORE);
    }

    private StaffAnswerResponse answer(UUID questionId, String answer) {
        return staffAnswerService.answer(userId, scanId, questionId, new StaffAnswerRequest(answer));
    }

    private Map<String, Object> menuRow() {
        return jdbcTemplate.queryForMap(
                "select risk_level, ai_risk_level, staff_answer_status from menu_analyses where id = ?", menuId);
    }

    private Map<String, Object> confirmation(UUID questionId) {
        return jdbcTemplate.queryForMap(
                """
                select c.present, c.source, c.invalidated_at is not null as invalidated
                from staff_questions q join ingredient_confirmations c on c.id = q.resolved_confirmation_id
                where q.id = ?
                """,
                questionId);
    }

    private UUID judgedMenu(int index, String name, long storeId, long storeMenuId, String risk, String staffStatus) {
        MenuAnalysis menu = menuAnalysisRepository.saveAndFlush(
                MenuAnalysis.prepared(scanId, index, name, null, null, null, null, storeId, storeMenuId));
        jdbcTemplate.update(
                """
                update menu_analyses
                set risk_level = ?, ai_risk_level = ?, status = 'completed', evidence_basis = 'confirmed',
                    information_status = 'complete', staff_answer_status = ?, display_order = ?
                where id = ?
                """,
                risk,
                risk,
                staffStatus,
                index + 1,
                menu.getId());
        return menu.getId();
    }

    private UUID question(
            long storeId, long storeMenuId, Long ingredientId, String flag, String askedTags, String cardId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into staff_questions (id, store_id, scan_session_id, store_menu_id, ingredient_id,
                                             ambiguity_flag, asked_tags, question_text, card_id)
                values (?, ?, ?, ?, ?, ?, cast(? as varchar[]), '{"ko":"질문","en":"question"}', ?)
                """,
                id,
                storeId,
                scanId,
                storeMenuId,
                ingredientId,
                flag,
                askedTags,
                cardId);
        return id;
    }

    private long insertStoreMenu(long storeId, String name) {
        return jdbcTemplate.queryForObject(
                """
                insert into store_menus (store_id, name_normalized, display_name, price_text, seen_count)
                values (?, ?, ?, '8,000원', 1) returning id
                """,
                Long.class,
                storeId,
                name,
                name);
    }

    private long insertStore() {
        Long categoryId = jdbcTemplate.queryForObject(
                "insert into store_categories(code, name, level) values (?, '음식', 1) returning id",
                Long.class,
                "Q" + UUID.randomUUID().toString().substring(0, 5));
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
                "Q" + UUID.randomUUID().toString().substring(0, 8),
                categoryId,
                batchId);
    }
}
