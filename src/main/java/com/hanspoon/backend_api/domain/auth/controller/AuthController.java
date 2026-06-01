package com.hanspoon.backend_api.domain.auth.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hanspoon.backend_api.domain.auth.dto.GoogleLoginRequest;
import com.hanspoon.backend_api.domain.auth.dto.LoginResponse;
import com.hanspoon.backend_api.domain.auth.dto.RefreshTokenRequest;
import com.hanspoon.backend_api.domain.auth.dto.TokenResponse;
import com.hanspoon.backend_api.domain.auth.service.AuthService;
import com.hanspoon.backend_api.domain.auth.service.LoginResult;
import com.hanspoon.backend_api.global.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/google")
	public ResponseEntity<LoginResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
		LoginResult result = authService.loginWithGoogle(request.idToken());
		HttpStatus status = result.newUser() ? HttpStatus.CREATED : HttpStatus.OK;
		return ResponseEntity.status(status).body(LoginResponse.from(result));
	}

	@PostMapping("/refresh")
	public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
		return TokenResponse.from(authService.refresh(request.refreshToken()));
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@CurrentUser String userId) {
		authService.logout(UUID.fromString(userId));
	}
}
