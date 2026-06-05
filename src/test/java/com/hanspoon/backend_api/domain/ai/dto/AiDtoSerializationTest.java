package com.hanspoon.backend_api.domain.ai.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanspoon.backend_api.domain.ai.dto.common.EscalationCase;
import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.domain.ai.dto.ocr.OcrRequest;
import com.hanspoon.backend_api.domain.ai.dto.ocr.OcrResponse;
import com.hanspoon.backend_api.domain.ai.dto.ruleengine.RuleMenuAnalysis;
import com.hanspoon.backend_api.domain.ai.dto.ruleengine.RuleProfile;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** AI 서비스 와이어 계약(snake_case)과 DTO 매핑 일치 검증. 프로덕션 HTTP 스택과 동일하게 Jackson 3 으로 검증. */
class AiDtoSerializationTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void deserializesDangerMenu() throws Exception {
        String json =
                """
                {
                  "menu_name_ko": "samgyeopsal",
                  "is_spicy": false,
                  "risk_level": "danger",
                  "hit_tags": ["is_pork"],
                  "triggered_flags": [],
                  "forbidden_tags": ["is_pork", "is_alcohol"],
                  "need_gpt": false,
                  "escalation_case": [],
                  "gpt_context": null,
                  "risk_reasons": [{"reason_type": "halal", "reason_ko": "pork included"}]
                }
                """;

        RuleMenuAnalysis result = objectMapper.readValue(json, RuleMenuAnalysis.class);

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.DANGER);
        assertThat(result.hitTags()).containsExactly("is_pork");
        assertThat(result.needGpt()).isFalse();
        assertThat(result.gptContext()).isNull();
        assertThat(result.riskReasons()).hasSize(1);
        assertThat(result.riskReasons().get(0).reasonType()).isEqualTo("halal");
    }

    @Test
    void deserializesAmbiguityMenuWithGptContext() throws Exception {
        String json =
                """
                {
                  "menu_name_ko": "sundubu",
                  "is_spicy": false,
                  "risk_level": "caution",
                  "hit_tags": [],
                  "triggered_flags": ["has_unclear_broth", "has_unclear_jeotgal"],
                  "forbidden_tags": ["is_alcohol", "is_pork"],
                  "need_gpt": true,
                  "escalation_case": ["ambiguity"],
                  "gpt_context": {
                    "base_menu": "sundubu",
                    "ingredients_explicit": ["sundubu", "egg"],
                    "explicit_tags": ["is_egg"],
                    "variant_tags": []
                  },
                  "risk_reasons": null
                }
                """;

        RuleMenuAnalysis result = objectMapper.readValue(json, RuleMenuAnalysis.class);

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.CAUTION);
        assertThat(result.needGpt()).isTrue();
        assertThat(result.escalationCase()).containsExactly(EscalationCase.AMBIGUITY);
        assertThat(result.triggeredFlags()).containsExactly("has_unclear_broth", "has_unclear_jeotgal");
        assertThat(result.gptContext().baseMenu()).isEqualTo("sundubu");
        assertThat(result.gptContext().explicitTags()).containsExactly("is_egg");
        assertThat(result.riskReasons()).isNull();
    }

    @Test
    void deserializesUnknownMenuEscalation() throws Exception {
        String json =
                """
                {
                  "menu_name_ko": "shrimp pasta",
                  "is_spicy": false,
                  "risk_level": "caution",
                  "hit_tags": [],
                  "triggered_flags": [],
                  "forbidden_tags": ["is_alcohol", "is_pork"],
                  "need_gpt": true,
                  "escalation_case": ["unknown_menu"],
                  "gpt_context": {
                    "base_menu": null,
                    "ingredients_explicit": [],
                    "explicit_tags": [],
                    "variant_tags": []
                  },
                  "risk_reasons": null
                }
                """;

        RuleMenuAnalysis result = objectMapper.readValue(json, RuleMenuAnalysis.class);

        assertThat(result.escalationCase()).containsExactly(EscalationCase.UNKNOWN_MENU);
        assertThat(result.gptContext().baseMenu()).isNull();
    }

    @Test
    void deserializesUnknownRemainEscalation() throws Exception {
        String json = "{\"menu_name_ko\":\"x\",\"need_gpt\":true,\"escalation_case\":[\"unknown_remain\"]}";

        RuleMenuAnalysis result = objectMapper.readValue(json, RuleMenuAnalysis.class);

        assertThat(result.escalationCase()).containsExactly(EscalationCase.UNKNOWN_REMAIN);
    }

    @Test
    void deserializesOcrResponseIgnoringUnknownFields() throws Exception {
        String json =
                """
                {
                  "scan_session": {
                    "title": "menu_001.jpg",
                    "menu_count": 2,
                    "risky_menu_count": null,
                    "scan_status": "completed",
                    "scanned_at": "2026-06-05T00:00:00Z"
                  },
                  "menu_image": {
                    "source": "upload",
                    "storage_key": "scans/menu_001.jpg",
                    "image_url": "https://example.com/scans/menu_001.jpg",
                    "mime_type": "image/jpeg",
                    "file_size": 123456
                  },
                  "scan_quality": {
                    "status": "usable",
                    "score": 72,
                    "raw_line_count": 20,
                    "price_match_count": 2,
                    "price_match_ratio": 1.0,
                    "image_width": 1280,
                    "image_height": 960,
                    "image_quality": {
                      "available": true,
                      "score": 80,
                      "blur_score": 120.5,
                      "brightness": 130.0,
                      "contrast": 50.0,
                      "glare_ratio": 0.02,
                      "skew_angle": 1.2,
                      "reasons": [],
                      "suggestions": [],
                      "future_unknown_field": "ignored"
                    },
                    "retake_suggestions": [],
                    "reasons": []
                  },
                  "menu_analyses": [
                    {
                      "menu_name_ko": "samgyeopsal",
                      "menu_name_en": null,
                      "description_ko": "",
                      "description_en": null,
                      "price_text": "9000",
                      "risk_level": null,
                      "is_spicy": false,
                      "image_url": null,
                      "display_order": 1
                    }
                  ],
                  "gpt_quality_judgment": {"verdict": "ok"}
                }
                """;

        OcrResponse result = objectMapper.readValue(json, OcrResponse.class);

        assertThat(result.scanSession().menuCount()).isEqualTo(2);
        assertThat(result.scanSession().riskyMenuCount()).isNull();
        assertThat(result.menuImage().storageKey()).isEqualTo("scans/menu_001.jpg");
        assertThat(result.scanQuality().status()).isEqualTo("usable");
        assertThat(result.scanQuality().imageQuality().glareRatio()).isEqualTo(0.02);
        assertThat(result.menuAnalyses()).hasSize(1);
        assertThat(result.menuAnalyses().get(0).priceText()).isEqualTo("9000");
        assertThat(result.menuAnalyses().get(0).riskLevel()).isNull();
        assertThat(result.menuAnalyses().get(0).isSpicy()).isFalse();
        assertThat(result.gptQualityJudgment().get("verdict").asString()).isEqualTo("ok");
    }

    @Test
    void serializesRuleProfileInSnakeCase() throws Exception {
        RuleProfile profile = new RuleProfile("halal", true, "vegan", true, List.of("is_milk"), false);

        String json = objectMapper.writeValueAsString(profile);

        assertThat(json)
                .contains("\"religion_type\":\"halal\"")
                .contains("\"is_vegetarian\":true")
                .contains("\"vegetarian_type\":\"vegan\"")
                .contains("\"no_alcohol\":true")
                .contains("\"allergies\":[\"is_milk\"]")
                .contains("\"no_spicy\":false");
    }

    @Test
    void serializesOcrRequestInSnakeCase() throws Exception {
        OcrRequest request = new OcrRequest("camera", "scans/menu_003.jpg", "https://example.com/scans/menu_003.jpg");

        String json = objectMapper.writeValueAsString(request);

        assertThat(json).contains("\"storage_key\":\"scans/menu_003.jpg\"").contains("\"image_url\":");
    }
}
