package com.hanspoon.backend_api.domain.scan.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanspoon.backend_api.domain.ai.dto.result.FinalMessage;
import com.hanspoon.backend_api.domain.ai.dto.result.OwnerCard;
import com.hanspoon.backend_api.domain.ai.dto.result.OwnerQuestion;
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
    void mapsLegacyMessageAndOwnerCard() {
        MenuAnalysis legacy = MenuAnalysis.create(
                UUID.randomUUID(),
                1,
                "순두부",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of("has_unclear_jeotgal"),
                new FinalMessage("젓갈 확인", "check jeotgal", null),
                new OwnerCard("순두부", "has_unclear_jeotgal", new OwnerQuestion("젓갈 넣나요?", null, null)));

        MenuResult result = MenuResultMapper.toMenuResult(legacy, null, null);

        assertThat(result.scanMenuId()).isEqualTo(legacy.getId());
        assertThat(result.message().get("ko").asString()).isEqualTo("젓갈 확인");
        assertThat(result.message().get("en").asString()).isEqualTo("check jeotgal");
        assertThat(result.ownerCard().menuName()).isEqualTo("순두부");
        assertThat(result.ownerCard().flag()).isEqualTo("has_unclear_jeotgal");
        assertThat(result.ownerCard().question().ko()).isEqualTo("젓갈 넣나요?");
        assertThat(result.riskIngredients()).isNull();
    }

    @Test
    void mapsJudgeMessageCardsAndRiskIngredients() {
        MenuAnalysis judged = MenuAnalysis.prepared(UUID.randomUUID(), 0, "제육볶음", null, null, null, null, null, null);
        ReflectionTestUtils.setField(judged, "message", "{\"verdict\":\"먹지 마세요\",\"reason\":\"돼지고기\",\"notes\":[]}");
        ReflectionTestUtils.setField(judged, "ownerCard", "[{\"card_id\":\"c1\"}]");
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

        MenuResult result = MenuResultMapper.toMenuResult(judged, null, "en");

        assertThat(result.message().get("verdict").asString()).isEqualTo("먹지 마세요");
        assertThat(result.ownerCard()).isNull();
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
