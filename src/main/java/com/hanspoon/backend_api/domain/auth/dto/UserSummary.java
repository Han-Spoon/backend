package com.hanspoon.backend_api.domain.auth.dto;

import java.util.UUID;

import com.hanspoon.backend_api.domain.user.entity.User;

public record UserSummary(
		UUID id,
		String email,
		String nickname,
		String languageCode) {

	public static UserSummary from(User user) {
		return new UserSummary(user.getId(), user.getEmail(), user.getNickname(), user.getLanguageCode());
	}
}
