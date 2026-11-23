package com.hanspoon.backend_api.domain.scan.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanspoon.backend_api.domain.scan.dto.MenuResult;
import com.hanspoon.backend_api.domain.scan.dto.RiskIngredientResult;
import com.hanspoon.backend_api.domain.scan.dto.ScanDietSnapshot;
import com.hanspoon.backend_api.domain.scan.entity.MenuAnalysis;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MenuResultMapperTest {

    @Test
    void passesThroughPreviousScanMessage() {
        MenuAnalysis previous =
                MenuAnalysis.prepared(UUID.randomUUID(), 0, "순두부", "순두부찌개", null, null, null, null, null);
        ReflectionTestUtils.setField(previous, "message", "{\"ko\":\"젓갈 확인\",\"en\":\"check jeotgal\",\"ar\":null}");
        ReflectionTestUtils.setField(previous, "matchedTags", List.of("has_unclear_jeotgal"));

        MenuResult result = MenuResultMapper.toMenuResult(previous, null, null, null, List.of());

        assertThat(result.scanMenuId()).isEqualTo(previous.getId());
        assertThat(result.displayName()).isEqualTo("순두부");
        assertThat(result.description()).isEqualTo("순두부찌개");
        assertThat(result.translatedName()).isNull();
        assertThat(result.translatedDescription()).isNull();
        assertThat(result.matchedTags()).containsExactly("has_unclear_jeotgal");
        assertThat(result.message().get("ko").asString()).isEqualTo("젓갈 확인");
        assertThat(result.message().get("en").asString()).isEqualTo("check jeotgal");
        assertThat(result.riskIngredients()).isNull();
    }

    @Test
    void mapsJudgeMessageAndRiskIngredients() {
        MenuAnalysis judged = MenuAnalysis.prepared(UUID.randomUUID(), 0, "제육볶음", null, null, null, null, null, null);
        ReflectionTestUtils.setField(judged, "message", "{\"verdict\":\"먹지 마세요\",\"reason\":\"돼지고기\",\"notes\":[]}");
        ReflectionTestUtils.setField(
                judged,
                "riskIngredients",
                """
                [{"ingredient":{"ingredient_id":1,"canonical_name":"돼지고기","localized_name":"Pork"},
                  "constraint_tags":["is_pork"],"matched_tags":["is_pork"],"role":"main","status":"present",
                  "posterior_mean":0.92,"confidence":0.8,"prior_source":"recipe","evidence_refs":[]},
                 {"ingredient":{"ingredient_id":2,"canonical_name":"소스","localized_name":null},
                  "constraint_tags":[],"matched_tags":[],"role":null,"status":"unknown",
                  "posterior_mean":null,"confidence":null,"prior_source":null,"evidence_refs":[]}]""");

        MenuResult result = MenuResultMapper.toMenuResult(judged, null, "Jeyuk bokkeum", "en", List.of());

        assertThat(result.translatedName()).isEqualTo("Jeyuk bokkeum");
        assertThat(result.message().get("verdict").asString()).isEqualTo("먹지 마세요");
        assertThat(result.riskIngredients())
                .containsExactly(
                        new RiskIngredientResult(
                                Map.of("ko", "돼지고기", "en", "Pork"),
                                "present",
                                0.92,
                                List.of("is_pork"),
                                0.8,
                                "main",
                                "recipe"),
                        new RiskIngredientResult(Map.of("ko", "소스"), "unknown", null, List.of(), null, null, null));
    }

    @Test
    void mapsDietSnapshotToProfileCodes() {
        ScanDietSnapshot snapshot = MenuResultMapper.dietSnapshot(
                """
                {"religion_type":"halal","is_vegetarian":true,"vegetarian_type":"lacto","no_alcohol":true,
                 "allergies":["is_milk","is_pinenut"],"no_spicy":false}""");

        assertThat(snapshot)
                .isEqualTo(new ScanDietSnapshot("halal", true, "lacto", List.of("milk", "pinenut"), false, true));
        assertThat(MenuResultMapper.dietSnapshot(null)).isNull();
    }
}
