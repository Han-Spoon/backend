package com.hanspoon.backend_api.domain.auth.dto;

import com.hanspoon.backend_api.domain.auth.service.TokenResult;

public record TokenResponse(String accessToken, String refreshToken) {

    public static TokenResponse from(TokenResult result) {
        return new TokenResponse(result.accessToken(), result.refreshToken());
    }
}
