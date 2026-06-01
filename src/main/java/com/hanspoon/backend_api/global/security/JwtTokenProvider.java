package com.hanspoon.backend_api.global.security;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * 자체 access token(HS256 JWT) 발급기. 검증은 {@code SecurityConfig#jwtDecoder}(OAuth2 Resource Server)가 담당한다.
 * 클레임: iss=issuer, sub=userId(UUID), iat, exp. {@code CurrentUser}가 sub를 주입한다.
 */
@Component
public class JwtTokenProvider {

	private final JwtEncoder jwtEncoder;
	private final String issuer;
	private final Duration accessTokenExpiration;

	public JwtTokenProvider(
			JwtEncoder jwtEncoder,
			@Value("${app.security.jwt.issuer}") String issuer,
			@Value("${app.security.jwt.access-token-expiration}") Duration accessTokenExpiration) {

		this.jwtEncoder = jwtEncoder;
		this.issuer = issuer;
		this.accessTokenExpiration = accessTokenExpiration;
	}

	public String createAccessToken(UUID userId) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(issuer)
				.issuedAt(now)
				.expiresAt(now.plus(accessTokenExpiration))
				.subject(userId.toString())
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	public Duration getAccessTokenExpiration() {
		return accessTokenExpiration;
	}
}
