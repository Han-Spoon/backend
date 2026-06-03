package com.hanspoon.backend_api.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 마이페이지 부분 수정. 두 필드 모두 선택(미전송 시 null → 변경 없음).
 * languageCode 는 ko/en/ar 만 허용(users.language_code 단일 출처).
 */
@Schema(description = "마이페이지 수정 요청(부분, 보낸 필드만 변경)")
public record UpdateUserRequest(
        @Schema(description = "표시 언어(ko/en/ar)", example = "en")
                @Pattern(regexp = "ko|en|ar", message = "languageCode must be one of ko, en, ar.") String languageCode,
        @Schema(description = "닉네임", example = "Master Kim")
                @Size(min = 1, max = 100, message = "nickname length must be between 1 and 100.") String nickname) {}
