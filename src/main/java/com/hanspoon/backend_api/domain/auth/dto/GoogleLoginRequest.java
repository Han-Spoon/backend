package com.hanspoon.backend_api.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "구글 로그인 요청")
public record GoogleLoginRequest(
        @Schema(description = "구글 ID Token") @NotBlank String idToken,
        @Schema(description = "이용자 연령 구간") @NotNull AgeGroup ageGroup,
        @Schema(description = "만 14세 미만인 경우 운영자가 법정대리인 동의를 검증한 뒤 전달한 승인 코드") String guardianCode) {}
