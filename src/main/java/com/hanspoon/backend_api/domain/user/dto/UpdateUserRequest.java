package com.hanspoon.backend_api.domain.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 마이페이지 부분 수정. 두 필드 모두 선택(미전송 시 null → 변경 없음).
 * languageCode 는 ko/en/ar 만 허용(users.language_code 단일 출처).
 */
public record UpdateUserRequest(
        @Pattern(regexp = "ko|en|ar", message = "languageCode must be one of ko, en, ar.") String languageCode,
        @Size(min = 1, max = 100, message = "nickname length must be between 1 and 100.") String nickname) {}
