package com.hanspoon.backend_api.domain.auth.dto;

import com.hanspoon.backend_api.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "사용자 요약")
public record UserSummary(
        @Schema(description = "사용자 ID") UUID id,
        @Schema(description = "이메일", example = "user@example.com") String email,
        @Schema(description = "닉네임", example = "Lee") String nickname,
        @Schema(description = "표시 언어(ko/en/ar)", example = "ko") String languageCode) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getEmail(), user.getNickname(), user.getLanguageCode());
    }
}
