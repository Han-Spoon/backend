package com.hanspoon.backend_api.domain.scan.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanspoon.backend_api.TestcontainersConfiguration;
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
