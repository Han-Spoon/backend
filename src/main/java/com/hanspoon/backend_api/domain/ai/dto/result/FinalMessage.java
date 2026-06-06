package com.hanspoon.backend_api.domain.ai.dto.result;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * 사용자 안내문 (다국어). ai_result FinalOutput.message. ko 필수, en/ar 은 생성 시에만.
 *
 * @param ko 한국어
 * @param en 영어
 * @param ar 아랍어
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record FinalMessage(String ko, String en, String ar) {}
