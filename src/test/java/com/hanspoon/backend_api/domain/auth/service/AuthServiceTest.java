package com.hanspoon.backend_api.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.hanspoon.backend_api.domain.auth.entity.AuthProvider;
import com.hanspoon.backend_api.domain.auth.entity.UserAuthIdentity;
import com.hanspoon.backend_api.domain.auth.entity.UserSession;
import com.hanspoon.backend_api.domain.auth.repository.UserAuthIdentityRepository;
import com.hanspoon.backend_api.domain.auth.repository.UserSessionRepository;
import com.hanspoon.backend_api.domain.user.entity.User;
import com.hanspoon.backend_api.domain.user.repository.UserProfileRepository;
import com.hanspoon.backend_api.domain.user.repository.UserRepository;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import com.hanspoon.backend_api.global.security.GoogleIdTokenVerifier;
import com.hanspoon.backend_api.global.security.JwtTokenProvider;
import com.hanspoon.backend_api.global.security.RefreshTokenSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserAuthIdentityRepository userAuthIdentityRepository;

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    private final RefreshTokenSupport refreshTokenSupport = new RefreshTokenSupport();

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                googleIdTokenVerifier,
                jwtTokenProvider,
                refreshTokenSupport,
                userRepository,
                userAuthIdentityRepository,
                userSessionRepository,
                userProfileRepository,
                Duration.ofDays(14));
    }

    private Jwt googleJwt(String sub, String email, String name) {
        return Jwt.withTokenValue("id-token")
                .header("alg", "RS256")
                .subject(sub)
                .claim("email", email)
                .claim("name", name)
                .build();
    }

    @Test
    void loginWithGoogle_newUser_createsUserAndIdentity() {
        when(googleIdTokenVerifier.verify(anyString())).thenReturn(googleJwt("g-123", "new@example.com", "Newbie"));
        when(userAuthIdentityRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "g-123"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtTokenProvider.createAccessToken(any(UUID.class))).thenReturn("access-token");
        when(userProfileRepository.existsByUserId(any(UUID.class))).thenReturn(false);

        LoginResult result = authService.loginWithGoogle("id-token");

        assertThat(result.newUser()).isTrue();
        assertThat(result.hasProfile()).isFalse();
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.user().getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void loginWithGoogle_existingIdentity_doesNotCreateUser() {
        User existing = User.create("user@example.com", "User", "en");
        UserAuthIdentity identity = UserAuthIdentity.google(existing.getId(), "g-999");
        when(googleIdTokenVerifier.verify(anyString())).thenReturn(googleJwt("g-999", "user@example.com", "User"));
        when(userAuthIdentityRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "g-999"))
                .thenReturn(Optional.of(identity));
        when(userRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(jwtTokenProvider.createAccessToken(existing.getId())).thenReturn("access-token");
        when(userProfileRepository.existsByUserId(existing.getId())).thenReturn(true);

        LoginResult result = authService.loginWithGoogle("id-token");

        assertThat(result.newUser()).isFalse();
        assertThat(result.hasProfile()).isTrue();
        assertThat(result.refreshToken()).isNotBlank();
    }

    @Test
    void refresh_activeSession_rotatesAndIssuesNewTokens() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        String rawToken = "raw-refresh-token";
        String hash = refreshTokenSupport.sha256Hex(rawToken);
        UserSession session = UserSession.issue(userId, hash, now.plus(Duration.ofDays(7)), now);

        when(userSessionRepository.findByRefreshTokenHash(hash)).thenReturn(Optional.of(session));
        when(jwtTokenProvider.createAccessToken(userId)).thenReturn("new-access-token");

        TokenResult result = authService.refresh(rawToken);

        assertThat(session.getRevokedAt()).isNotNull(); // 기존 세션 폐기(회전)
        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(result.refreshToken()).isNotBlank().isNotEqualTo(rawToken);
    }

    @Test
    void refresh_expiredSession_throwsInvalidToken() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        String rawToken = "expired-token";
        String hash = refreshTokenSupport.sha256Hex(rawToken);
        UserSession expired =
                UserSession.issue(userId, hash, now.minus(Duration.ofMinutes(1)), now.minus(Duration.ofDays(15)));
        when(userSessionRepository.findByRefreshTokenHash(hash)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh(rawToken))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void refresh_unknownToken_throwsInvalidToken() {
        when(userSessionRepository.findByRefreshTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("nope"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void logout_revokesAllActiveSessions() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        UserSession s1 = UserSession.issue(userId, "h1", now.plus(Duration.ofDays(1)), now);
        UserSession s2 = UserSession.issue(userId, "h2", now.plus(Duration.ofDays(1)), now);
        when(userSessionRepository.findAllByUserIdAndRevokedAtIsNull(eq(userId)))
                .thenReturn(List.of(s1, s2));

        authService.logout(userId);

        assertThat(s1.getRevokedAt()).isNotNull();
        assertThat(s2.getRevokedAt()).isNotNull();
    }
}
