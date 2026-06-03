package com.hanspoon.backend_api.domain.auth.controller;

import com.hanspoon.backend_api.domain.auth.dto.GoogleLoginRequest;
import com.hanspoon.backend_api.domain.auth.dto.LoginResponse;
import com.hanspoon.backend_api.domain.auth.dto.RefreshTokenRequest;
import com.hanspoon.backend_api.domain.auth.dto.TokenResponse;
import com.hanspoon.backend_api.domain.auth.service.AuthService;
import com.hanspoon.backend_api.domain.auth.service.LoginResult;
import com.hanspoon.backend_api.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "구글 로그인 / 토큰 갱신 / 로그아웃 API")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "구글 로그인(ID Token 검증 → JWT 발급). 신규 가입 시 201")
    @PostMapping("/google")
    public ResponseEntity<LoginResponse> loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        LoginResult result = authService.loginWithGoogle(request.idToken());
        HttpStatus status = result.newUser() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(LoginResponse.from(result));
    }

    @Operation(summary = "access token 갱신(refresh token 회전)")
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return TokenResponse.from(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "로그아웃(사용자 전체 세션 폐기)")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CurrentUser String userId) {
        authService.logout(UUID.fromString(userId));
    }
}
