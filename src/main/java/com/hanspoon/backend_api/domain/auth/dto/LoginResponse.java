package com.hanspoon.backend_api.domain.auth.dto;

import com.hanspoon.backend_api.domain.auth.service.LoginResult;

public record LoginResponse(
		String accessToken,
		String refreshToken,
		boolean hasProfile,
		UserSummary user) {

	public static LoginResponse from(LoginResult result) {
		return new LoginResponse(
				result.accessToken(),
				result.refreshToken(),
				result.hasProfile(),
				UserSummary.from(result.user()));
	}
}
