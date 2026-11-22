package com.hanspoon.backend_api.domain.ai.dto.analyze;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** 표준 재료 참조. 미매핑 재료는 ingredientId null. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record IngredientRef(Long ingredientId, String canonicalName) {}
