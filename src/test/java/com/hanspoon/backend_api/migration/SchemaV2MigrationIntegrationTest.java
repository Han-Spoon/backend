package com.hanspoon.backend_api.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hanspoon.backend_api.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flyway V8~V13(스키마 v2 확장 단계)의 핵심 DB 계약을 실제 PostgreSQL 에서 검증한다.
 * 전체 무결성 시나리오(303건)는 설계 단계에서 별도 SQL 로 검증했다(2026-11-15, erd/DECISIONS.md §8).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SchemaV2MigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsAllSchemaV2TablesWithComments() {
        Integer tables = jdbcTemplate.queryForObject(
                "select count(*) from pg_class where relnamespace = 'public'::regnamespace and relkind = 'r'"
                        + " and relname <> 'flyway_schema_history'",
                Integer.class);
        Integer uncommentedColumns = jdbcTemplate.queryForObject(
                """
                select count(*)
                  from pg_attribute a join pg_class c on c.oid = a.attrelid
                 where c.relnamespace = 'public'::regnamespace and c.relkind = 'r'
                   and c.relname <> 'flyway_schema_history'
                   and a.attnum > 0 and not a.attisdropped
                   and col_description(c.oid, a.attnum) is null
                """,
                Integer.class);

        // 스키마 v1 16개 - user_allergies + 약관 동의(V7) 1개 + 스키마 v2 신규 35개
        assertThat(tables).isEqualTo(51);
        assertThat(uncommentedColumns).isZero();
    }

    @Test
    void normalizesFoodNamesLikeStoreNamesAndUsesGistForKnnCandidateLookup() {
        assertThat(jdbcTemplate.queryForObject("select normalize_food_name(?)", String.class, "■ 김치 찌개 –"))
                .isEqualTo("김치찌개");
        assertThat(jdbcTemplate.queryForObject(
                        "select indexdef from pg_indexes where indexname = 'idx_menus_name_trgm'", String.class))
                .contains("USING gist");
    }

    @Test
    void rejectsStoreMenuFromAnotherStoreOnScanMenu() {
        Fixture f = fixture();
        long otherStoreMenu = insertStoreMenu(f.otherStoreId(), "김치찌개", f.menuId());

        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        insert into menu_analyses (id, scan_session_id, source_index, store_id, store_menu_id)
                        values (?, ?, 0, ?, ?)
                        """,
                        UUID.randomUUID(),
                        f.scanId(),
                        f.storeId(),
                        otherStoreMenu))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_menu_analyses_store_menu");
    }

    @Test
    void asksStaffQuestionOncePerStoreMenuTarget() {
        Fixture f = fixture();
        long storeMenu = insertStoreMenu(f.storeId(), "김치찌개", f.menuId());
        insertQuestion(f, storeMenu, "card:0:ingredient-1");

        // 실패한 문장 뒤로는 같은 트랜잭션에서 실행할 수 없으므로 마지막에 검증한다
        assertThatThrownBy(() -> insertQuestion(f, storeMenu, "card:0:ingredient-1-retry"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_staff_questions_ingredient");
    }

    @Test
    void rejectsStaffQuestionOnAnotherStoresMenu() {
        Fixture f = fixture();
        long otherStoreMenu = insertStoreMenu(f.otherStoreId(), "김치찌개", f.menuId());

        assertThatThrownBy(() -> insertQuestion(f, otherStoreMenu, "card:0:ingredient-1"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_staff_questions_store_menu");
    }

    @Test
    void confirmsStaffAnswerImmediatelyAndRequiresTheLink() {
        Fixture f = fixture();
        long storeMenu = insertStoreMenu(f.storeId(), "김치찌개", f.menuId());
        UUID question = insertQuestion(f, storeMenu, "card:0:ingredient-1");
        long confirmation = insertConfirmation(f, storeMenu, true);
        answer(question, "present", confirmation);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "update staff_questions set resolved_confirmation_id = null where id = ?", question))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_staff_questions_resolved");
    }

    @Test
    void letsStaffCorrectAnAnswerOnTheSameQuestion() {
        Fixture f = fixture();
        long storeMenu = insertStoreMenu(f.storeId(), "김치찌개", f.menuId());
        UUID question = insertQuestion(f, storeMenu, "card:0:ingredient-1");
        long confirmation = insertConfirmation(f, storeMenu, false);
        answer(question, "absent", confirmation);

        // 잘못 누른 답을 같은 질문에서 고친다: 확정값을 갱신하고 같은 행을 덮어쓴다
        jdbcTemplate.update("update ingredient_confirmations set present = true where id = ?", confirmation);
        answer(question, "present", confirmation);
        // 모름으로 고치면 연결을 끊고 확정값을 사용 중지한다
        jdbcTemplate.update(
                "update staff_questions set answer = 'unknown', resolved_confirmation_id = null where id = ?",
                question);
        jdbcTemplate.update("update ingredient_confirmations set invalidated_at = now() where id = ?", confirmation);

        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from staff_questions where store_menu_id = ? and answer = 'unknown'",
                        Integer.class,
                        storeMenu))
                .isEqualTo(1);
    }

    @Test
    void autoApprovesWebRecipeBatchWithoutAnomalySignals() {
        Fixture f = fixture();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        insert into knowledge_review_items (store_id, item_type, proposed_name_ko, payload)
                        values (?, 'web_search_menu', '마라탕', '{}')
                        """,
                        f.storeId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_knowledge_review_items_routing");
    }

    @Test
    void defaultsStaffAnswerStatusToNoneForCurrentPipelineRows() {
        Fixture f = fixture();
        UUID analysisId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into menu_analyses (id, scan_session_id, menu_name_ko, risk_level) values (?, ?, '김치찌개', 'safe')",
                analysisId,
                f.scanId());

        assertThat(jdbcTemplate.queryForObject(
                        "select staff_answer_status from menu_analyses where id = ?", String.class, analysisId))
                .isEqualTo("none");
    }

    @Test
    void keepsGlobalAiCommandsStoreless() {
        Fixture f = fixture();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        insert into ai_command_inbox (action, idempotency_key, store_id, request_hash, payload)
                        values ('save_web_search_cache', ?, ?, ?, '{}')
                        """,
                        "sha256:" + "a".repeat(64),
                        f.storeId(),
                        "b".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ai_command_inbox_store_scope");
    }

    @Test
    void rejectsWebRecipeAsStoreEvidence() {
        Fixture f = fixture();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        insert into ingredient_evidence_log
                            (store_id, menu_id, ingredient_id, source_type, delta_alpha, delta_beta)
                        values (?, ?, ?, 'web_search', 1, 0)
                        """,
                        f.storeId(),
                        f.menuId(),
                        f.ingredientId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void usesDeployedConsentTableInsteadOfTermsTables() {
        Integer termsTables = jdbcTemplate.queryForObject(
                "select count(*) from pg_class where relname in ('terms_versions', 'user_terms_agreements')",
                Integer.class);
        UUID userId = insertUser();
        jdbcTemplate.update(
                """
                insert into user_consent_decisions (id, user_id, document_id, document_version, accepted)
                values (?, ?, 'marketing', '2026-11-14', false)
                """,
                UUID.randomUUID(),
                userId);

        assertThat(termsTables).isZero();
        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        insert into user_consent_decisions (id, user_id, document_id, document_version, accepted)
                        values (?, ?, 'location', '2026-11-14', true)
                        """,
                        UUID.randomUUID(),
                        userId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_consent_document_id");
    }

    @Test
    void exposesStorePriorViewWithBayesianContractColumns() {
        String columns = jdbcTemplate.queryForObject(
                """
                select string_agg(column_name, ',' order by ordinal_position)
                  from information_schema.columns
                 where table_schema = 'public' and table_name = 'ingredient_risk_scores'
                """,
                String.class);
        String relkind = jdbcTemplate.queryForObject(
                "select relkind::text from pg_class where relname = 'ingredient_risk_scores'", String.class);

        assertThat(relkind).isEqualTo("v");
        assertThat(columns).contains("store_id", "menu_id", "ingredient_id", "alpha", "beta", "evidence_count");
    }

    @Test
    void keepsAuditLogUnchangedWhenAdminAccountIsDeleted() {
        UUID adminId = insertUser();
        jdbcTemplate.update(
                """
                insert into admin_audit_logs (admin_user_id, action, target_type, target_id)
                values (?, 'review_item.approve', 'knowledge_review_item', '1')
                """,
                adminId);

        jdbcTemplate.update("delete from users where id = ?", adminId);

        assertThat(jdbcTemplate.queryForObject(
                        "select admin_user_id from admin_audit_logs where target_id = '1'", UUID.class))
                .isEqualTo(adminId);
    }

    @Test
    void rejectsJudgeRawBeforeJudgement() {
        Fixture f = fixture();
        jdbcTemplate.update(
                """
                insert into scan_ai_results
                    (scan_session_id, prepare_id, schema_version, prepared_items_hash, prepared_items, prepare_signals)
                values (?, ?, '3.0', ?, '[]', '{}')
                """,
                f.scanId(),
                UUID.randomUUID(),
                "sha256:" + "a".repeat(64));

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "update scan_ai_results set judge_raw = '{}' where scan_session_id = ?", f.scanId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_scan_ai_results_judge_raw");
    }

    @Test
    void requiresAllergyCodesOnProfileWithoutDefault() {
        UUID userId = insertUser();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "insert into user_profiles (id, user_id) values (?, ?)", UUID.randomUUID(), userId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("allergy_codes");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{is_egg}", "{alcohol}", "{shrimp,egg}", "{egg,egg}"})
    void rejectsPrefixedUnknownUnsortedOrDuplicateAllergyCodes(String codes) {
        UUID userId = insertUser();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "insert into user_profiles (id, user_id, allergy_codes) values (?, ?, ?::varchar[])",
                        UUID.randomUUID(),
                        userId,
                        codes))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_user_profiles_allergy_codes");
    }

    @Test
    void storesSortedFoodSafetyAllergyArrayInPlaceOfLegacyTable() {
        UUID userId = insertUser();
        jdbcTemplate.update(
                "insert into user_profiles (id, user_id, allergy_codes) values (?, ?, '{egg,peanut,shrimp}')",
                UUID.randomUUID(),
                userId);

        assertThat(jdbcTemplate.queryForObject(
                        "select ? = any(allergy_codes) from user_profiles where user_id = ?",
                        Boolean.class,
                        "peanut",
                        userId))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject("select to_regclass('user_allergies') is null", Boolean.class))
                .isTrue();
    }

    @Test
    void keepsLegacyPipelineColumnsUntilContractPhase() {
        Integer legacyColumns = jdbcTemplate.queryForObject(
                """
                select count(*) from information_schema.columns
                 where table_schema = 'public'
                   and ((table_name = 'menu_analyses'
                         and column_name in ('menu_name_ko', 'menu_name_en', 'description_en', 'owner_card', 'hit_tags'))
                     or (table_name = 'scan_sessions' and column_name = 'risky_menu_count'))
                """,
                Integer.class);

        assertThat(legacyColumns).isEqualTo(6);
    }

    @Test
    void rejectsMealReviewLinkedToAnotherUsersScan() {
        Fixture f = fixture();
        UUID otherUser = insertUser();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "insert into meal_reviews (id, user_id, store_id, scan_session_id) values (?, ?, ?, ?)",
                        UUID.randomUUID(),
                        otherUser,
                        f.storeId(),
                        f.scanId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_meal_reviews_scan");
    }

    @Test
    void allowsMealReviewWithoutScanAndWithTypedMenuName() {
        Fixture f = fixture();
        UUID review = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into meal_reviews (id, user_id, store_id) values (?, ?, ?)", review, scanOwner(f), f.storeId());
        jdbcTemplate.update(
                "insert into meal_review_items (id, review_id, menu_name) values (?, ?, '계란말이')",
                UUID.randomUUID(),
                review);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "insert into meal_review_items (id, review_id, menu_name) values (?, ?, '계란 말이')",
                        UUID.randomUUID(),
                        review))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_meal_review_items_menu");
    }

    @Test
    void requiresSourceLocaleForMealReviewComment() {
        Fixture f = fixture();
        UUID review = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into meal_reviews (id, user_id, store_id) values (?, ?, ?)", review, scanOwner(f), f.storeId());

        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        insert into meal_review_items (id, review_id, menu_name, comment)
                        values (?, ?, '김치찌개', '맛있어요')
                        """,
                        UUID.randomUUID(),
                        review))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_meal_review_items_comment_locale");
    }

    @ParameterizedTest
    @ValueSource(strings = {"allergy:alcohol", "religion:none", "vegetarian:vegan", "preference:no_spicy", "is_egg"})
    void rejectsDietCodeOutsideScreenProfileItems(String dietCode) {
        UUID item = insertMealReviewItem(fixture());

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "insert into meal_review_answers (review_item_id, diet_code, answer) values (?, ?, 'present')",
                        item,
                        dietCode))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_meal_review_answers_diet_code");
    }

    @Test
    void deletesMealReviewWithItsScan() {
        Fixture f = fixture();
        UUID item = insertMealReviewItem(f);
        jdbcTemplate.update(
                "insert into meal_review_answers (review_item_id, diet_code, answer) values (?, 'vegan:vegan', 'absent')",
                item);

        jdbcTemplate.update("delete from scan_sessions where id = ?", f.scanId());

        Integer remaining = jdbcTemplate.queryForObject(
                """
                select (select count(*) from meal_reviews) + (select count(*) from meal_review_items)
                     + (select count(*) from meal_review_answers)
                """,
                Integer.class);
        assertThat(remaining).isZero();
    }

    @Test
    void requiresStoreOnMealReview() {
        Fixture f = fixture();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "insert into meal_reviews (id, user_id, scan_session_id) values (?, ?, ?)",
                        UUID.randomUUID(),
                        scanOwner(f),
                        f.scanId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("store_id");
    }

    @Test
    void acceptsOtherReportReasonWithoutDetailButOnlyOncePerReporter() {
        Fixture f = fixture();
        insertMealReviewItem(f);
        UUID review = jdbcTemplate.queryForObject(
                "select id from meal_reviews where scan_session_id = ?", UUID.class, f.scanId());
        UUID reporter = insertUser();
        jdbcTemplate.update(
                "insert into meal_review_reports (id, review_id, reporter_user_id, reason) values (?, ?, ?, 'other')",
                UUID.randomUUID(),
                review,
                reporter);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "insert into meal_review_reports (id, review_id, reporter_user_id, reason) values (?, ?, ?, 'spam')",
                        UUID.randomUUID(),
                        review,
                        reporter))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_meal_review_reports_reporter");
    }

    @Test
    void rejectsSafeJudgeRowWithoutInformationStatus() {
        Fixture f = fixture();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        insert into menu_analyses (id, scan_session_id, source_index, risk_level, ai_risk_level, status)
                        values (?, ?, 0, 'safe', 'safe', 'completed')
                        """,
                        UUID.randomUUID(),
                        f.scanId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_menu_analyses_judged_information");
    }

    @Test
    void rejectsFavoriteOnAnotherUsersScanMenu() {
        Fixture f = fixture();
        UUID menu = insertMenuAnalysis(f.scanId(), 0);
        UUID otherUser = insertUser();

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "insert into favorite_menus (user_id, menu_analysis_id, scan_session_id) values (?, ?, ?)",
                        otherUser,
                        menu,
                        f.scanId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_favorite_menus_scan");
    }

    @Test
    void deletesFavoriteWithItsScan() {
        Fixture f = fixture();
        UUID menu = insertMenuAnalysis(f.scanId(), 0);
        UUID owner =
                jdbcTemplate.queryForObject("select user_id from scan_sessions where id = ?", UUID.class, f.scanId());
        jdbcTemplate.update(
                "insert into favorite_menus (user_id, menu_analysis_id, scan_session_id) values (?, ?, ?)",
                owner,
                menu,
                f.scanId());

        jdbcTemplate.update("delete from scan_sessions where id = ?", f.scanId());

        assertThat(jdbcTemplate.queryForObject("select count(*) from favorite_menus", Integer.class))
                .isZero();
    }

    @Test
    void rejectsAreaWithoutKoreanName() {
        assertThatThrownBy(
                        () -> jdbcTemplate.update(
                                """
                        insert into areas (name_i18n, center_lat, center_lng, radius_m)
                        values ('{"en":"Hongdae"}', 37.55, 126.92, 1500)
                        """))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_areas_name");
    }

    private long insertConfirmation(Fixture f, long storeMenuId, boolean present) {
        return jdbcTemplate.queryForObject(
                """
                insert into ingredient_confirmations (store_id, store_menu_id, ingredient_id, present, confirmed_at)
                values (?, ?, ?, ?, now())
                returning id
                """,
                Long.class,
                f.storeId(),
                storeMenuId,
                f.ingredientId(),
                present);
    }

    private void answer(UUID question, String answer, long confirmation) {
        jdbcTemplate.update(
                """
                update staff_questions
                   set status = 'answered', answer = ?, answered_at = now(), resolved_confirmation_id = ?
                 where id = ?
                """,
                answer,
                confirmation,
                question);
    }

    private UUID insertMenuAnalysis(UUID scanId, int sourceIndex) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into menu_analyses (id, scan_session_id, source_index, menu_name_ko) values (?, ?, ?, '김치찌개')",
                id,
                scanId,
                sourceIndex);
        return id;
    }

    private UUID scanOwner(Fixture f) {
        return jdbcTemplate.queryForObject("select user_id from scan_sessions where id = ?", UUID.class, f.scanId());
    }

    private UUID insertMealReviewItem(Fixture f) {
        UUID menu = insertMenuAnalysis(f.scanId(), 0);
        UUID review = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into meal_reviews (id, user_id, store_id, scan_session_id) values (?, ?, ?, ?)",
                review,
                scanOwner(f),
                f.storeId(),
                f.scanId());
        UUID item = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into meal_review_items (id, review_id, menu_analysis_id, menu_name, comment, comment_locale)
                values (?, ?, ?, '김치찌개', 'No shrimp in the broth.', 'en')
                """,
                item,
                review,
                menu);
        return item;
    }

    private Fixture fixture() {
        long categoryId = jdbcTemplate.queryForObject(
                "insert into store_categories (code, name, level) values ('I2', '음식', 1) returning id", Long.class);
        long batchId = jdbcTemplate.queryForObject(
                """
                insert into store_import_batches (source, source_version, row_count, status, finished_at)
                values ('sbiz', '202606', 2, 'completed', now())
                returning id
                """,
                Long.class);
        long storeId = insertStore(categoryId, batchId, "S-ERD-1");
        long otherStoreId = insertStore(categoryId, batchId, "S-ERD-2");
        UUID userId = insertUser();
        UUID scanId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into scan_sessions (id, user_id, scan_status, store_id, store_name_snapshot, store_match_method)
                values (?, ?, 'processing', ?, '한스푼', 'gps_candidate')
                """,
                scanId,
                userId,
                storeId);
        long menuId = jdbcTemplate.queryForObject(
                "insert into menus (name_ko, source, needs_review) values ('김치찌개', 'curated', true) returning id",
                Long.class);
        long ingredientId = jdbcTemplate.queryForObject(
                "insert into ingredients (name_ko) values ('돼지고기') returning id", Long.class);
        return new Fixture(storeId, otherStoreId, scanId, menuId, ingredientId);
    }

    private long insertStore(long categoryId, long batchId, String storeNo) {
        return jdbcTemplate.queryForObject(
                """
                insert into stores (sbiz_store_no, name, category_id, lat, lng, origin, last_batch_id)
                values (?, '한스푼', ?, 37.5, 127.0, 'sbiz', ?)
                returning id
                """,
                Long.class,
                storeNo,
                categoryId,
                batchId);
    }

    private long insertStoreMenu(long storeId, String name, long menuId) {
        return jdbcTemplate.queryForObject(
                """
                insert into store_menus (store_id, name_normalized, display_name, menu_id, mapping_source)
                values (?, normalize_food_name(?), ?, ?, 'name_match')
                returning id
                """,
                Long.class,
                storeId,
                name,
                name,
                menuId);
    }

    private UUID insertQuestion(Fixture f, long storeMenuId, String cardId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                insert into staff_questions
                    (id, store_id, scan_session_id, store_menu_id, menu_id, ingredient_id, question_text, card_id)
                values (?, ?, ?, ?, ?, ?, '{"ko":"돼지고기가 들어가나요?"}', ?)
                """,
                id,
                f.storeId(),
                f.scanId(),
                storeMenuId,
                f.menuId(),
                f.ingredientId(),
                cardId);
        return id;
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into users (id, email, nickname, language_code) values (?, ?, 'tester', 'ko')",
                userId,
                "erd-" + userId + "@example.com");
        return userId;
    }

    private record Fixture(long storeId, long otherStoreId, UUID scanId, long menuId, long ingredientId) {}
}
