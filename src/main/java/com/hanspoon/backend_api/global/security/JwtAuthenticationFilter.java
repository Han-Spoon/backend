package com.hanspoon.backend_api.global.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final BearerTokenResolver bearerTokenResolver = new DefaultBearerTokenResolver();
	private final JwtDecoder jwtDecoder;
	private final JwtAuthenticationConverter authenticationConverter;

	public JwtAuthenticationFilter(JwtDecoder jwtDecoder, JwtAuthenticationConverter authenticationConverter) {
		this.jwtDecoder = jwtDecoder;
		this.authenticationConverter = authenticationConverter;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {

		String token = bearerTokenResolver.resolve(request);
		if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
			Jwt jwt = jwtDecoder.decode(token);
			Authentication authentication = authenticationConverter.convert(jwt);
			SecurityContextHolder.getContext().setAuthentication(authentication);
		}

		filterChain.doFilter(request, response);
	}
}
