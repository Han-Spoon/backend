package com.hanspoon.backend_api.domain.user.dto;

import com.hanspoon.backend_api.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "마이페이지 응답")
public record UserMeResponse(
        @Schema(description = "사용자 ID") UUID id,
        @Schema(description = "이메일", example = "user@example.com") String email,
        @Schema(description = "닉네임", example = "Lee") String nickname,
        @Schema(description = "표시 언어(ko/en/ar)", example = "ko") String languageCode,
        @Schema(description = "프로필 존재 여부", example = "true") boolean hasProfile) {

    public static UserMeResponse of(User user, boolean hasProfile) {
        return new UserMeResponse(
                user.getId(), user.getEmail(), user.getNickname(), user.getLanguageCode(), hasProfile);
    }
}
