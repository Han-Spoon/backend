package com.hanspoon.backend_api.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
		@NotBlank String idToken) {
}
