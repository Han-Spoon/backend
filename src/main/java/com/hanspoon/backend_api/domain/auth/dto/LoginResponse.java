package com.hanspoon.backend_api.domain.auth.dto;

import com.hanspoon.backend_api.domain.auth.service.LoginResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 응답")
public record LoginResponse(
        @Schema(description = "access token(JWT, HS256)") String accessToken,
        @Schema(description = "refresh token") String refreshToken,
        @Schema(description = "프로필 존재 여부(온보딩 완료 여부)", example = "false") boolean hasProfile,
        @Schema(description = "사용자 요약") UserSummary user) {

    public static LoginResponse from(LoginResult result) {
        return new LoginResponse(
                result.accessToken(), result.refreshToken(), result.hasProfile(), UserSummary.from(result.user()));
    }
}
