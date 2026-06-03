package com.hanspoon.backend_api.domain.auth.dto;

import com.hanspoon.backend_api.domain.auth.service.TokenResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "토큰 갱신 응답")
public record TokenResponse(
        @Schema(description = "새 access token(JWT)") String accessToken,
        @Schema(description = "새 refresh token(회전됨)") String refreshToken) {

    public static TokenResponse from(TokenResult result) {
        return new TokenResponse(result.accessToken(), result.refreshToken());
    }
}
