package com.hanspoon.backend_api.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "토큰 갱신 요청")
public record RefreshTokenRequest(
        @Schema(description = "불투명 refresh token", example = "8f3b2c1a...") @NotBlank String refreshToken) {}
