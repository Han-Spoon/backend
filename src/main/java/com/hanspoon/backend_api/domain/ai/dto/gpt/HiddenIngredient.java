package com.hanspoon.backend_api.domain.ai.dto.gpt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * GPT 가 추론한 숨은 재료.
 *
 * @param name 재료명 (예: "멸치 육수")
 * @param source 출처 재료 (예: "broth")
 * @param taxonomy 분류 ("broth" | "sauce" | "fermented" | ...)
 * @param allergyTags 발생 알레르기 태그 (is_* 형식)
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record HiddenIngredient(String name, String source, String taxonomy, List<String> allergyTags) {}
