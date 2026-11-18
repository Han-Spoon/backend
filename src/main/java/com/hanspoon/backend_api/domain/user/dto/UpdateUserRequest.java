package com.hanspoon.backend_api.domain.user.dto;

import com.hanspoon.backend_api.global.validation.SupportedLanguages;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 마이페이지 부분 수정. 두 필드 모두 선택(미전송 시 null → 변경 없음).
 * languageCode 는 지원 언어 7종만 허용(users.language_code 단일 출처).
 */
@Schema(description = "마이페이지 수정 요청(부분, 보낸 필드만 변경)")
public record UpdateUserRequest(
        @Schema(description = "표시 언어(7종: ko, en, ar, zh-CN, zh-TW, ja, es)", example = "en")
                @Pattern(regexp = SupportedLanguages.PATTERN, message = SupportedLanguages.MESSAGE) String languageCode,
        @Schema(description = "닉네임", example = "Master Kim")
                @Size(min = 1, max = 100, message = "nickname length must be between 1 and 100.") String nickname) {}
