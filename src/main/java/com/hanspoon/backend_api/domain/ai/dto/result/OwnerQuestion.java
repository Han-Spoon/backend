package com.hanspoon.backend_api.domain.ai.dto.result;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * 사장님 소통 카드의 확인 질문 (다국어).
 *
 * @param ko 한국어
 * @param en 영어
 * @param ar 아랍어
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public record OwnerQuestion(String ko, String en, String ar) {}
