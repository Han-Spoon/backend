package com.hanspoon.backend_api.domain.ai.dto.ruleengine;

import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * 룰엔진 입력 프로필 (백엔드 → AI 서비스). 키는 {@code ai_ruleengine/main.py} 코드 기준.
 *
 * @param religionType 종교 식단 ("halal" | "kosher" | "hindu" | null)
 * @param isVegetarian 채식 여부
 * @param vegetarianType 채식 유형 ("vegan" | "lacto" | ... | null)
 * @param noAlcohol 금주 여부
 * @param allergies 알레르기 태그 목록 (is_* 형식, 예: "is_milk")
 * @param noSpicy 매운맛 비선호 여부
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RuleProfile(
        String religionType,
        Boolean isVegetarian,
        String vegetarianType,
        Boolean noAlcohol,
        List<String> allergies,
        Boolean noSpicy) {}
