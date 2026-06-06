package com.hanspoon.backend_api.domain.ai.dto.gpt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * 다국어 텍스트 (한/영/아랍어). 미생성 언어는 null.
 *
 * @param ko 한국어
 * @param en 영어
 * @param ar 아랍어
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record LocalizedText(String ko, String en, String ar) {}
