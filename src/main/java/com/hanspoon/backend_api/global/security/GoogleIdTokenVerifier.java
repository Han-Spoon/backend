package com.hanspoon.backend_api.global.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;

@Component
public class GoogleIdTokenVerifier {

	private static final List<String> GOOGLE_ISSUERS = List.of("https://accounts.google.com", "accounts.google.com");

	private final JwtDecoder jwtDecoder;
	private final String clientId;

	public GoogleIdTokenVerifier(
			@Value("${app.security.google.jwk-set-uri:https://www.googleapis.com/oauth2/v3/certs}") String jwkSetUri,
			@Value("${app.security.google.client-id:}") String clientId) {

		this.jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
		this.clientId = clientId;
	}

	public Jwt verify(String idToken) {
		try {
			Jwt jwt = jwtDecoder.decode(idToken);
			validateIssuer(jwt);
			validateAudience(jwt);
			return jwt;
		} catch (JwtException | IllegalArgumentException exception) {
			throw new BusinessException(ErrorCode.INVALID_TOKEN, "Invalid Google ID token.", exception);
		}
	}

	private void validateIssuer(Jwt jwt) {
		String issuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
		if (!GOOGLE_ISSUERS.contains(issuer)) {
			throw new BusinessException(ErrorCode.INVALID_TOKEN, "Invalid Google token issuer.");
		}
	}

	private void validateAudience(Jwt jwt) {
		if (!StringUtils.hasText(clientId)) {
			return;
		}
		if (!jwt.getAudience().contains(clientId)) {
			throw new BusinessException(ErrorCode.INVALID_TOKEN, "Invalid Google token audience.");
		}
	}
}
