package com.hanspoon.backend_api.domain.user.dto;

import com.hanspoon.backend_api.domain.user.entity.User;
import java.util.UUID;

public record UserMeResponse(UUID id, String email, String nickname, String languageCode, boolean hasProfile) {

    public static UserMeResponse of(User user, boolean hasProfile) {
        return new UserMeResponse(
                user.getId(), user.getEmail(), user.getNickname(), user.getLanguageCode(), hasProfile);
    }
}
