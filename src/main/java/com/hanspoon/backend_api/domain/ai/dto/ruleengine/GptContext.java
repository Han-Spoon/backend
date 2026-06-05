package com.hanspoon.backend_api.domain.ai.dto.ruleengine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * GPT 에스컬레이션 시 함께 넘길 컨텍스트 (환각 방지용). needGpt=true 일 때만 채워진다.
 *
 * @param baseMenu CSV 에서 매칭된 베이스 메뉴명 (unknown_menu 면 null)
 * @param ingredientsExplicit CSV 레시피 재료 목록
 * @param explicitTags 명시 재료에서 잡힌 태그
 * @param variantTags remain 토큰에서 잡힌 태그
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record GptContext(
        String baseMenu, List<String> ingredientsExplicit, List<String> explicitTags, List<String> variantTags) {}
