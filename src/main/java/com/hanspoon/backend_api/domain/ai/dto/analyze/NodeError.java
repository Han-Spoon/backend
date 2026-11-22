package com.hanspoon.backend_api.domain.ai.dto.analyze;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** AI 노드 오류. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record NodeError(String code, String node, String itemId, String message, boolean retryable, String fallback) {}
