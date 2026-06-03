package com.hanspoon.backend_api.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "구글 로그인 요청")
public record GoogleLoginRequest(@Schema(description = "구글 ID Token") @NotBlank String idToken) {}
