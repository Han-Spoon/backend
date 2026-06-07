package com.hanspoon.backend_api.domain.card.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * 카드 표시 문구(다국어). ko(사장님 제시용) 필수 + 사용자 언어(en/ar) 1개. JSONB 로 저장된다.
 *
 * @param ko 한국어 (필수)
 * @param en 영어 (선택)
 * @param ar 아랍어 (선택)
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "카드 표시 문구(다국어)")
public record CardText(@NotBlank String ko, String en, String ar) {}
