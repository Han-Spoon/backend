package com.hanspoon.backend_api.domain.ai.dto.gpt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * GPT 가 판단한 위험 설명.
 *
 * @param triggeredTag 발동된 유저 제한 태그 (예: "is_seafood")
 * @param reason 왜 위험한지 설명
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record GptRisk(String triggeredTag, String reason) {}
