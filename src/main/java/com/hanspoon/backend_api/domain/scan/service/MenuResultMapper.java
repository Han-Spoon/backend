package com.hanspoon.backend_api.domain.scan.service;

import com.hanspoon.backend_api.domain.ai.dto.result.OwnerCard;
import com.hanspoon.backend_api.domain.ai.dto.result.OwnerQuestion;
import com.hanspoon.backend_api.domain.scan.dto.MenuResult;
import com.hanspoon.backend_api.domain.scan.dto.RiskIngredientResult;
import com.hanspoon.backend_api.domain.scan.dto.ScanDietSnapshot;
import com.hanspoon.backend_api.domain.scan.entity.MenuAnalysis;
import com.hanspoon.backend_api.domain.scan.service.MenuPhotoResolver.MenuPhoto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 저장 JSON(레거시·Judge 형식) → 결과 응답. */
final class MenuResultMapper {

    private static final JsonMapper JSON = JsonMapper.shared();
    private static final String ALLERGY_TAG_PREFIX = "is_";
    private static final String KO = "ko";

    private MenuResultMapper() {}

    static MenuResult toMenuResult(MenuAnalysis m, MenuPhoto photo, String locale) {
        return new MenuResult(
                m.getId(),
                m.getDisplayOrder(),
                m.getMenuNameKo(),
                m.getMenuNameEn(),
                m.getDescriptionKo(),
                m.getDescriptionEn(),
                m.getPriceText(),
                photo == null ? null : photo.url(),
                photo == null ? null : photo.attribution(),
                m.getIsSpicy(),
                m.getRiskLevel(),
                m.getStatus(),
                m.getEvidenceBasis(),
                m.getInformationStatus(),
                m.getStaffAnswerStatus(),
                riskIngredients(m.getRiskIngredients(), locale),
                m.getHitTags(),
                object(m.getMessage()),
                legacyOwnerCard(m.getOwnerCard()));
    }

    /** DietProfile(snake_case, is_* 알레르기) → 유저 프로필 코드값. */
    static ScanDietSnapshot dietSnapshot(String json) {
        JsonNode node = object(json);
        if (node == null) {
            return null;
        }
        List<String> allergies = new ArrayList<>();
        for (String tag : strings(node.get("allergies"))) {
            allergies.add(tag.startsWith(ALLERGY_TAG_PREFIX) ? tag.substring(ALLERGY_TAG_PREFIX.length()) : tag);
        }
        return new ScanDietSnapshot(
                text(node, "religion_type"),
                bool(node, "is_vegetarian"),
                text(node, "vegetarian_type"),
                allergies,
                bool(node, "no_spicy"),
                bool(node, "no_alcohol"));
    }

    /** Judge 메뉴만. 레거시 메뉴는 null. */
    private static List<RiskIngredientResult> riskIngredients(String json, String locale) {
        JsonNode array = parse(json);
        if (array == null || !array.isArray()) {
            return null;
        }
        List<RiskIngredientResult> results = new ArrayList<>();
        for (JsonNode item : array) {
            JsonNode ingredient = item.path("ingredient");
            Map<String, String> name = new LinkedHashMap<>();
            name.put(KO, text(ingredient, "canonical_name"));
            String localized = text(ingredient, "localized_name");
            if (locale != null && !KO.equals(locale) && localized != null) {
                name.put(locale, localized);
            }
            results.add(new RiskIngredientResult(
                    name,
                    text(item, "status"),
                    number(item, "posterior_mean"),
                    strings(item.get("matched_tags")),
                    number(item, "confidence"),
                    text(item, "role"),
                    text(item, "prior_source")));
        }
        return results;
    }

    /** 레거시 단일 카드만. Judge 카드 배열은 null. */
    private static OwnerCard legacyOwnerCard(String json) {
        JsonNode node = object(json);
        if (node == null) {
            return null;
        }
        JsonNode question = node.get("question");
        return new OwnerCard(
                text(node, "menuName"),
                text(node, "flag"),
                question == null || !question.isObject()
                        ? null
                        : new OwnerQuestion(text(question, KO), text(question, "en"), text(question, "ar")));
    }

    private static JsonNode object(String json) {
        JsonNode node = parse(json);
        return node != null && node.isObject() ? node : null;
    }

    private static JsonNode parse(String json) {
        return json == null ? null : JSON.readTree(json);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static Double number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asDouble() : null;
    }

    private static Boolean bool(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isBoolean() ? value.asBoolean() : null;
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        if (array != null && array.isArray()) {
            array.forEach(value -> values.add(value.asString()));
        }
        return values;
    }
}
