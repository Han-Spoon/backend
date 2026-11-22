package com.hanspoon.backend_api.domain.scan.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanspoon.backend_api.TestcontainersConfiguration;
import com.hanspoon.backend_api.domain.ai.dto.analyze.JudgeResponse;
import com.hanspoon.backend_api.domain.ai.dto.analyze.JudgeResult;
import com.hanspoon.backend_api.domain.ai.dto.analyze.PreparedItem;
import com.hanspoon.backend_api.domain.scan.entity.ScanAiResult;
import com.hanspoon.backend_api.domain.scan.entity.ScanImage;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.store.entity.StoreMatchMethod;
import com.hanspoon.backend_api.domain.user.entity.User;
import com.hanspoon.backend_api.domain.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AnalyzeStateWriterIntegrationTest {

    private static final String HASH = "sha256:" + "0".repeat(63) + "1";

    @Autowired
    private AnalyzeStateWriter analyzeStateWriter;

    @Autowired
    private ScanSessionRepository scanSessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void savesPrepareResultWithStoreMenusInOneTransaction() {
        long storeId = insertStore();
        Long curatedId = jdbcTemplate.queryForObject(
                """
                insert into store_menus (store_id, name_normalized, display_name, price_text, seen_count)
                values (?, '된장찌개', '된장찌개(시연)', '9,000원', 3) returning id
                """,
                Long.class,
                storeId);
        ScanSession session = startScan(storeId);
        UUID scanId = session.getId();
        String items = """
                [%s, %s, %s, %s]
                """
                .formatted(
                        item(
                                scanId,
                                0,
                                "김치 찌개",
                                "김치찌개",
                                "[{\"label\":\"2인\",\"price_text\":\"16,000원\",\"inferred\":false}]",
                                true),
                        item(scanId, 1, "김치찌개 ", "김치찌개", "[]", false),
                        item(scanId, 2, "된장 찌개", "된장찌개", "[]", false),
                        item(scanId, 3, "■ –", "■ –", "[]", false));

        var analyses = analyzeStateWriter.applyPrepareResult(
                image(scanId), aiResult(scanId, items), readItems(items), "en", Instant.parse("2026-11-22T00:00:00Z"));

        assertThat(analyses).hasSize(4);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                select source_index, menu_name_ko, store_id, store_menu_id, price_options::text as price_options,
                       is_spicy, risk_level
                from menu_analyses where scan_session_id = ? order by source_index
                """,
                scanId);
        assertThat(rows).extracting(row -> row.get("source_index")).containsExactly(0, 1, 2, 3);
        assertThat(rows.get(0).get("menu_name_ko")).isEqualTo("김치 찌개");
        assertThat(rows.get(0).get("is_spicy")).isEqualTo(true);
        assertThat(rows.get(1).get("is_spicy")).isNull();
        assertThat(rows.get(0).get("price_options").toString()).contains("16,000원");
        assertThat(rows.get(1).get("price_options")).isEqualTo("[]");
        assertThat(rows.get(0).get("risk_level")).isNull();
        assertThat(rows).allSatisfy(row -> assertThat(row.get("store_id")).isEqualTo(storeId));
        // 같은 스캔의 같은 이름은 한 가게 메뉴, 빈 정규화 이름은 연결 없음
        assertThat(rows.get(0).get("store_menu_id")).isEqualTo(rows.get(1).get("store_menu_id"));
        assertThat(rows.get(2).get("store_menu_id")).isEqualTo(curatedId);
        assertThat(rows.get(3).get("store_menu_id")).isNull();

        Map<String, Object> created = jdbcTemplate.queryForMap(
                "select display_name, seen_count, last_scan_session_id from store_menus where id = ?",
                rows.get(0).get("store_menu_id"));
        assertThat(created.get("display_name")).isEqualTo("김치 찌개");
        assertThat(created.get("seen_count")).isEqualTo(1);
        assertThat(created.get("last_scan_session_id")).isEqualTo(scanId);
        // 기존 가게 메뉴는 표시값 유지, 확인 정보만 갱신
        Map<String, Object> curated = jdbcTemplate.queryForMap(
                "select display_name, price_text, seen_count, last_scan_session_id from store_menus where id = ?",
                curatedId);
        assertThat(curated.get("display_name")).isEqualTo("된장찌개(시연)");
        assertThat(curated.get("price_text")).isEqualTo("9,000원");
        assertThat(curated.get("seen_count")).isEqualTo(4);
        assertThat(curated.get("last_scan_session_id")).isEqualTo(scanId);

        Map<String, Object> ai = jdbcTemplate.queryForMap(
                """
                select prepared_items_hash, jsonb_array_length(prepared_items) as item_count,
                       prepared_items -> 0 ->> 'origin_text' as origin_text,
                       jsonb_exists(prepared_items -> 0, 'origin_text') as has_origin_text,
                       prepare_signals ->> 'scan_quality_status' as quality, judged_at
                from scan_ai_results where scan_session_id = ?
                """,
                scanId);
        assertThat(ai.get("prepared_items_hash")).isEqualTo(HASH);
        assertThat(ai.get("item_count")).isEqualTo(4);
        assertThat(ai.get("has_origin_text")).isEqualTo(true);
        assertThat(ai.get("origin_text")).isNull();
        assertThat(ai.get("quality")).isEqualTo("usable");
        assertThat(ai.get("judged_at")).isNull();

        ScanSession reloaded = scanSessionRepository.findById(scanId).orElseThrow();
        assertThat(reloaded.getScanStatus()).isEqualTo(ScanStatus.PROCESSING);
        assertThat(reloaded.getMenuCount()).isEqualTo(4);
        assertThat(reloaded.getLocale()).isEqualTo("en");
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from scan_images where scan_session_id = ?", Integer.class, scanId))
                .isEqualTo(1);
    }

    @Test
    void storelessScanSkipsStoreMenus() {
        User user = userRepository.save(User.create(uniqueEmail("storeless"), "storeless", "ko"));
        ScanSession session = scanSessionRepository.saveAndFlush(
                ScanSession.startWithoutStore(user.getId(), "scans/" + user.getId() + "/storeless.jpg"));
        UUID scanId = session.getId();
        String items = "[" + item(scanId, 0, "김치찌개", "김치찌개", "[]", false) + "]";

        analyzeStateWriter.applyPrepareResult(
                image(scanId), aiResult(scanId, items), readItems(items), "ko", Instant.now());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select store_id, store_menu_id from menu_analyses where scan_session_id = ?", scanId);
        assertThat(row.get("store_id")).isNull();
        assertThat(row.get("store_menu_id")).isNull();
    }

    @Test
    void retakeStoresImageAndReasonsOnly() {
        ScanSession session = startScan(insertStore());
        UUID scanId = session.getId();

        analyzeStateWriter.applyPrepareRetake(image(scanId), scanId, List.of("blur"), List.of("hold_steady"));

        ScanSession reloaded = scanSessionRepository.findById(scanId).orElseThrow();
        assertThat(reloaded.getScanStatus()).isEqualTo(ScanStatus.NEEDS_RETAKE);
        assertThat(reloaded.getRetakeReasons()).containsExactly("blur");
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from scan_ai_results where scan_session_id = ?", Integer.class, scanId))
                .isZero();
    }

    @Test
    void savesJudgeResultCountersMetaAndStaffQuestions() {
        long storeId = insertStore();
        String tag = UUID.randomUUID().toString().substring(0, 8);
        long porkId = jdbcTemplate.queryForObject(
                "insert into ingredients (name_ko, tag_codes) values (?, '{is_pork}') returning id",
                Long.class,
                "돼지" + tag);
        long menuId = jdbcTemplate.queryForObject(
                "insert into menus (name_ko, source, needs_review) values (?, 'curated', true) returning id",
                Long.class,
                "제육" + tag);
        ScanSession session = startScan(storeId);
        UUID scanId = session.getId();
        String items = "[%s, %s, %s]"
                .formatted(
                        item(scanId, 0, "제육" + tag, "제육" + tag, "[]", false),
                        item(scanId, 1, "공기밥", "공기밥", "[]", false),
                        item(scanId, 2, "알수없음", "알수없음", "[]", false));
        var analyses = analyzeStateWriter.applyPrepareResult(
                image(scanId), aiResult(scanId, items), readItems(items), "en", Instant.now());
        String ingredientCard = card(
                scanId,
                0,
                "ingredient",
                menuId,
                "{\"ingredient_id\":%d,\"canonical_name\":\"돼지\"}".formatted(porkId),
                "null",
                "[]");
        String flagCard =
                card(scanId, 0, "flag", menuId, "null", "\"has_unclear_jeotgal\"", "[\"is_shrimp\",\"is_fish\"]");
        String unmappedCard =
                card(scanId, 0, "unmapped", menuId, "{\"ingredient_id\":null,\"canonical_name\":\"소스\"}", "null", "[]");
        String raw =
                """
                {"schema_version":"3.0","trace_id":"%s","prepare_id":"%s","scan_session_id":"%s","store_id":%d,
                 "prepared_items_hash":"%s","bundle_hash":"sha256:%s","knowledge_revision":"2026-11-22T00:00:00Z",
                 "items":[%s,%s,%s],
                 "evidence":[],
                 "persistence_commands":[%s,%s,%s,%s],
                 "warnings":[],"errors":[]}
                """
                        .formatted(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                scanId,
                                storeId,
                                HASH,
                                "2".repeat(64),
                                judged(
                                        scanId,
                                        0,
                                        menuId,
                                        "fuzzy",
                                        "danger",
                                        "confirmed",
                                        "complete",
                                        "partial",
                                        "[\"is_pork\"]",
                                        "[" + ingredientCard + "]"),
                                judged(scanId, 1, null, null, "safe", "confirmed", "complete", "complete", "[]", "[]"),
                                judged(scanId, 2, null, null, "caution", "unknown", "none", "none", "[]", "[]"),
                                command("create_owner_verification_request", "a", scanId, ingredientCard),
                                command("create_owner_verification_request", "b", scanId, flagCard),
                                command("create_owner_verification_request", "c", scanId, unmappedCard),
                                command("save_menu_ingredient_cache", "d", null, "{\"menu_id\":%d}".formatted(menuId)));
        JsonNode rawNode = jsonMapper.readTree(raw);
        String diet =
                """
                {"religion_type":"halal","is_vegetarian":false,"vegetarian_type":null,"no_alcohol":true,
                 "allergies":[],"no_spicy":false}""";

        analyzeStateWriter.applyJudgeResult(
                scanId, analyses, new JudgeResult(jsonMapper.treeToValue(rawNode, JudgeResponse.class), rawNode), diet);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                select source_index, risk_level, ai_risk_level, display_order, menu_id, menu_match_type,
                       staff_answer_status, evidence_basis, hit_tags::text as hit_tags, message ->> 'verdict' as verdict,
                       jsonb_array_length(owner_card) as cards, jsonb_array_length(risk_ingredients) as risks
                from menu_analyses where scan_session_id = ? order by source_index
                """,
                scanId);
        assertThat(rows).extracting(row -> row.get("risk_level")).containsExactly("danger", "safe", "caution");
        assertThat(rows).extracting(row -> row.get("ai_risk_level")).containsExactly("danger", "safe", "caution");
        // SAFE → CAUTION+근거 부족 → DANGER
        assertThat(rows).extracting(row -> row.get("display_order")).containsExactly(3, 1, 2);
        assertThat(rows.get(0).get("menu_id")).isEqualTo(menuId);
        assertThat(rows.get(0).get("menu_match_type")).isEqualTo("fuzzy");
        assertThat(rows.get(2).get("menu_match_type")).isEqualTo("unmatched");
        assertThat(rows.get(0).get("staff_answer_status")).isEqualTo("partial");
        assertThat(rows.get(0).get("hit_tags")).isEqualTo("[\"is_pork\"]");
        assertThat(rows.get(0).get("verdict")).isEqualTo("verdict-0");
        assertThat(rows.get(0).get("cards")).isEqualTo(1);
        assertThat(rows.get(1).get("risks")).isEqualTo(0);

        Map<String, Object> mapping = jdbcTemplate.queryForMap(
                """
                select sm.menu_id, sm.mapping_source from store_menus sm
                join menu_analyses ma on ma.store_menu_id = sm.id
                where ma.scan_session_id = ? and ma.source_index = 0
                """,
                scanId);
        assertThat(mapping.get("menu_id")).isEqualTo(menuId);
        assertThat(mapping.get("mapping_source")).isEqualTo("fuzzy");

        ScanSession reloaded = scanSessionRepository.findById(scanId).orElseThrow();
        assertThat(reloaded.getScanStatus()).isEqualTo(ScanStatus.COMPLETED);
        assertThat(reloaded.getDangerCount()).isEqualTo(1);
        assertThat(reloaded.getCautionCount()).isEqualTo(1);
        assertThat(reloaded.getSafeCount()).isEqualTo(1);
        assertThat(reloaded.getRiskyMenuCount()).isEqualTo(2);
        assertThat(jsonMapper
                        .readTree(reloaded.getDietSnapshot())
                        .get("religion_type")
                        .asString())
                .isEqualTo("halal");

        Map<String, Object> meta = jdbcTemplate.queryForMap(
                """
                select knowledge_revision, bundle_hash, jsonb_typeof(judge_evidence) as evidence,
                       judge_raw ->> 'scan_session_id' as raw_scan, judged_at
                from scan_ai_results where scan_session_id = ?
                """,
                scanId);
        assertThat(meta.get("knowledge_revision")).isEqualTo("2026-11-22T00:00:00Z");
        assertThat(meta.get("bundle_hash")).isEqualTo("sha256:" + "2".repeat(64));
        assertThat(meta.get("evidence")).isEqualTo("array");
        assertThat(meta.get("raw_scan")).isEqualTo(scanId.toString());
        assertThat(meta.get("judged_at")).isNotNull();

        List<Map<String, Object>> inbox = jdbcTemplate.queryForList(
                """
                select action, status, store_id, result ->> 'skipped' as skipped,
                       result ->> 'staff_question_id' as question_id
                from ai_command_inbox where scan_session_id = ? order by idempotency_key
                """,
                scanId);
        assertThat(inbox)
                .extracting(row -> row.get("status"))
                .containsExactly("applied", "applied", "applied", "received");
        assertThat(inbox.get(0).get("question_id")).isNotNull();
        assertThat(inbox.get(2).get("skipped")).isEqualTo("ingredient_unmapped");
        assertThat(inbox.get(3).get("store_id")).isNull();
        assertThat(inbox.get(0).get("store_id")).isEqualTo(storeId);

        List<Map<String, Object>> questions = jdbcTemplate.queryForList(
                """
                select ingredient_id, ambiguity_flag, array_to_string(asked_tags, ',') as tags, menu_id,
                       question_text ->> 'ko' as ko, jsonb_exists(question_text, 'fr') as has_fr, card_id, status
                from staff_questions where scan_session_id = ? order by card_id
                """,
                scanId);
        assertThat(questions).hasSize(2);
        assertThat(questions.get(0).get("ambiguity_flag")).isEqualTo("has_unclear_jeotgal");
        assertThat(questions.get(0).get("tags")).isEqualTo("is_fish,is_shrimp");
        assertThat(questions.get(1).get("ingredient_id")).isEqualTo(porkId);
        assertThat(questions.get(1).get("tags")).isNull();
        assertThat(questions).allSatisfy(question -> {
            assertThat(question.get("menu_id")).isEqualTo(menuId);
            assertThat(question.get("ko")).isEqualTo("질문");
            assertThat(question.get("has_fr")).isEqualTo(false);
            assertThat(question.get("status")).isEqualTo("pending");
        });
    }

    private String judged(
            UUID scanId,
            int index,
            Long menuId,
            String matchType,
            String risk,
            String basis,
            String information,
            String ownerAnswer,
            String matchedTags,
            String ownerCards) {
        return """
                {"item_id":"%s:%d","source_index":%d,"menu_id":%s,"menu_category_id":null,
                 "normalization_status":"%s","match_type":%s,"selection_method":"deterministic","status":"completed",
                 "route":"exact_only","risk_level":"%s","evidence_basis":"%s","information_status":"%s",
                 "owner_answer_status":"%s","risk_ingredients":[],"matched_tags":%s,"evidence_refs":[],
                 "message":{"verdict":"verdict-%d","reason":null,"notes":[]},"owner_cards":%s,"warnings":[],"errors":[]}
                """
                .formatted(
                        scanId,
                        index,
                        index,
                        menuId == null ? "null" : menuId,
                        menuId == null ? "unmatched" : "normalized",
                        matchType == null ? "null" : "\"" + matchType + "\"",
                        risk,
                        basis,
                        information,
                        ownerAnswer,
                        matchedTags,
                        index,
                        ownerCards);
    }

    private String card(UUID scanId, int index, String name, long menuId, String ingredient, String flag, String tags) {
        return """
                {"item_id":"%s:%d","card_id":"card:%s:%d:%s","menu_id":%d,"ingredient":%s,"ambiguity_flag":%s,
                 "constraint_tags":%s,"question":{"ko":"질문","en":"question","fr":"ignored"},
                 "reason_codes":["insufficient_recipe_evidence"],"evidence_refs":[]}
                """
                .formatted(scanId, index, scanId, index, name, menuId, ingredient, flag, tags);
    }

    private String command(String action, String suffix, UUID scanId, String payload) {
        String itemId = scanId == null ? "null" : "\"" + scanId + ":0\"";
        return """
                {"command_id":"command-%s","operation_id":null,"item_id":%s,"card_id":null,"action":"%s",
                 "idempotency_key":"sha256:%s","payload":%s}
                """
                .formatted(suffix, itemId, action, suffix.repeat(64), payload);
    }

    private ScanSession startScan(long storeId) {
        User user = userRepository.save(User.create(uniqueEmail("prepare"), "prepare", "ko"));
        return scanSessionRepository.saveAndFlush(ScanSession.start(
                user.getId(), "scans/" + user.getId() + "/menu.jpg", storeId, "한스푼", StoreMatchMethod.NAME_SEARCH));
    }

    private String item(
            UUID scanId, int index, String displayName, String normalized, String priceOptions, boolean spicy) {
        String observations = spicy
                ? """
                  [{"tag":"is_spicy","confidence":0.9,"evidence":{"evidence_id":"ev-%d","evidence_class":"direct",
                    "source_type":"ocr_text","source_ref":"menu","verification_status":"observed",
                    "reliability_weight":null,"observed_at":"2026-11-22T00:00:00Z"}}]
                  """
                        .formatted(index)
                : "[]";
        return """
                {"item_id":"%s:%d","source_index":%d,"raw_menu_name":"%s","origin_text":null,
                 "foreign_text_segments":[],"normalized_menu_name":"%s","display_name":"%s","description":null,
                 "price_text":"8,000원","price_options":%s,"ocr_confidence":0.95,"locale_hint":"ko","options":[],
                 "residual_tokens":[],"risk_tag_observations":%s}
                """
                .formatted(scanId, index, index, displayName, normalized, displayName, priceOptions, observations);
    }

    private List<PreparedItem> readItems(String items) {
        return jsonMapper.readerForListOf(PreparedItem.class).readValue(items);
    }

    private ScanAiResult aiResult(UUID scanId, String items) {
        return ScanAiResult.prepared(
                scanId,
                UUID.randomUUID(),
                "3.0",
                HASH,
                items,
                "{\"scan_quality_status\":\"usable\",\"warnings\":[]}",
                Instant.now());
    }

    private ScanImage image(UUID scanId) {
        return ScanImage.create(
                scanId, "camera", "scans/" + scanId + ".jpg", "s3://test/" + scanId, "image/jpeg", 10L, "v1", "\"e\"");
    }

    /** 반경 검색 테스트와 겹치지 않는 좌표. */
    private long insertStore() {
        Long categoryId = jdbcTemplate.queryForObject(
                "insert into store_categories(code, name, level) values (?, '음식', 1) returning id",
                Long.class,
                "I" + UUID.randomUUID().toString().substring(0, 5));
        Long batchId = jdbcTemplate.queryForObject(
                "insert into store_import_batches(source, source_version) values ('sbiz', ?) returning id",
                Long.class,
                String.valueOf(100000 + Math.abs(UUID.randomUUID().hashCode() % 899999)));
        return jdbcTemplate.queryForObject(
                """
                insert into stores(sbiz_store_no, name, category_id, lat, lng, origin, last_batch_id)
                values (?, '한스푼', ?, 33.4, 126.5, 'sbiz', ?) returning id
                """,
                Long.class,
                "S" + UUID.randomUUID().toString().substring(0, 8),
                categoryId,
                batchId);
    }

    private String uniqueEmail(String prefix) {
        return prefix + "+" + UUID.randomUUID() + "@example.com";
    }
}
