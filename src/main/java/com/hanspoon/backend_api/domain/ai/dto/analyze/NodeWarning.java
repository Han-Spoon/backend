package com.hanspoon.backend_api.domain.ai.dto.analyze;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** AI 경고. forcesCaution 이면 SAFE 불가. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record NodeWarning(String code, String node, String itemId, String message, boolean forcesCaution) {}
