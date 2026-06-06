package com.hanspoon.backend_api.domain.ai.dto.ruleengine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * 사용자에게 보여줄 위험 사유. 룰엔진이 직접 채우는 경우(needGpt=false, hitTags 존재)에만 존재.
 *
 * @param reasonType 사유 유형 ("halal" | "hindu" | "allergy" | "vegetarian" 등)
 * @param reasonKo 한국어 안내문
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record RiskReason(String reasonType, String reasonKo) {}
