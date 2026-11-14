package com.hanspoon.backend_api.domain.auth.service;

import com.hanspoon.backend_api.domain.auth.dto.AgeGroup;
import com.hanspoon.backend_api.domain.auth.entity.AuthProvider;
import com.hanspoon.backend_api.domain.auth.entity.UserAuthIdentity;
import com.hanspoon.backend_api.domain.auth.entity.UserSession;
import com.hanspoon.backend_api.domain.auth.repository.UserAuthIdentityRepository;
import com.hanspoon.backend_api.domain.auth.repository.UserSessionRepository;
import com.hanspoon.backend_api.domain.consent.service.ConsentService;
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
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenSupport refreshTokenSupport;
    private final UserRepository userRepository;
    private final UserAuthIdentityRepository userAuthIdentityRepository;
    private final UserSessionRepository userSessionRepository;
    private final UserProfileRepository userProfileRepository;
    private final GuardianApprovalService guardianApprovalService;
    private final ConsentService consentService;
    private final Duration refreshTokenExpiration;
    private final Duration refreshRotationGrace;

    public AuthService(
            GoogleIdTokenVerifier googleIdTokenVerifier,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenSupport refreshTokenSupport,
            UserRepository userRepository,
            UserAuthIdentityRepository userAuthIdentityRepository,
            UserSessionRepository userSessionRepository,
            UserProfileRepository userProfileRepository,
            GuardianApprovalService guardianApprovalService,
            ConsentService consentService,
            @Value("${app.security.jwt.refresh-token-expiration}") Duration refreshTokenExpiration,
            @Value("${app.security.jwt.refresh-rotation-grace}") Duration refreshRotationGrace) {

        this.googleIdTokenVerifier = googleIdTokenVerifier;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenSupport = refreshTokenSupport;
        this.userRepository = userRepository;
        this.userAuthIdentityRepository = userAuthIdentityRepository;
        this.userSessionRepository = userSessionRepository;
        this.userProfileRepository = userProfileRepository;
        this.guardianApprovalService = guardianApprovalService;
        this.consentService = consentService;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.refreshRotationGrace = refreshRotationGrace;
    }

    @Transactional
    public LoginResult loginWithGoogle(String idToken, AgeGroup ageGroup, String guardianCode) {
        // 승인 코드를 먼저 검사하고 행을 잠근다. 미승인 아동의 Google ID Token은 검증하지 않는다.
        UUID guardianApprovalId = null;
        if (ageGroup == AgeGroup.UNDER_14) {
            guardianApprovalId = guardianApprovalService.lockValidApproval(
                    guardianCode, consentService.getDocuments().version());
            if (guardianApprovalId == null) {
                throw new BusinessException(ErrorCode.GUARDIAN_CONSENT_REQUIRED);
            }
        } else if (ageGroup != AgeGroup.OVER_14 || guardianCode != null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Jwt googleToken = googleIdTokenVerifier.verify(idToken);
        String providerId = googleToken.getSubject();
        String email = googleToken.getClaimAsString("email");
        String name = googleToken.getClaimAsString("name");

        if (email == null || email.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "Google token missing email claim.");
        }

        User user;
        boolean newUser;

        UserAuthIdentity identity = userAuthIdentityRepository
                .findByProviderAndProviderId(AuthProvider.GOOGLE, providerId)
                .orElse(null);

        if (identity != null) {
            user = userRepository
                    .findById(identity.getUserId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            newUser = false;
        } else {
            User existing = userRepository.findByEmail(email).orElse(null);
            if (existing != null) {
                // 동일 이메일이 이미 가입됨 → 신규 식별자만 연결(중복 가입/이메일 충돌 방지)
                user = existing;
                newUser = false;
            } else {
                user = userRepository.save(User.create(email, resolveNickname(name, email), "en", ageGroup));
                newUser = true;
            }
            userAuthIdentityRepository.save(UserAuthIdentity.google(user.getId(), providerId));
        }

        if (user.getAgeGroup() != ageGroup) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Age group does not match this account.");
        }
        if (guardianApprovalId != null) {
            guardianApprovalService.linkToUser(guardianApprovalId, user.getId());
            consentService.recordGuardianConsent(user.getId());
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        String refreshToken = issueRefreshSession(user.getId());
        boolean hasProfile = userProfileRepository.existsByUserId(user.getId());

        return new LoginResult(accessToken, refreshToken, hasProfile, newUser, user);
    }

    @Transactional
    public TokenResult refresh(String rawRefreshToken) {
        String hash = refreshTokenSupport.sha256Hex(rawRefreshToken);
        UserSession session = userSessionRepository
                .findByRefreshTokenHash(hash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN, "Invalid refresh token."));

        Instant now = Instant.now();
        if (!session.isActive(now) && !session.isWithinRotationGrace(now, refreshRotationGrace)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "Expired or revoked refresh token.");
        }
        if (!consentService.hasActiveGuardianApprovalIfMinor(session.getUserId())) {
            throw new BusinessException(ErrorCode.GUARDIAN_CONSENT_REQUIRED);
        }

        // 회전: 기존 세션 폐기 후 신규 발급
        session.revoke(now);
        String newRefreshToken = issueRefreshSession(session.getUserId());
        String accessToken = jwtTokenProvider.createAccessToken(session.getUserId());

        return new TokenResult(accessToken, newRefreshToken);
    }

    @Transactional
    public void logout(UUID userId) {
        Instant now = Instant.now();
        userSessionRepository.findAllByUserIdAndRevokedAtIsNull(userId).forEach(session -> session.revoke(now));
    }

    private String issueRefreshSession(UUID userId) {
        String rawToken = refreshTokenSupport.generateRawToken();
        String hash = refreshTokenSupport.sha256Hex(rawToken);
        Instant now = Instant.now();
        userSessionRepository.save(UserSession.issue(userId, hash, now.plus(refreshTokenExpiration), now));
        return rawToken;
    }

    private String resolveNickname(String name, String email) {
        if (name != null && !name.isBlank()) {
            return name;
        }
        int atIndex = email.indexOf('@');
        return atIndex > 0 ? email.substring(0, atIndex) : "user";
    }
}
