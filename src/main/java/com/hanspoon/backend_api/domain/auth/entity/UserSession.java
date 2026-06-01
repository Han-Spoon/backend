package com.hanspoon.backend_api.domain.auth.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSession {

	@Id
	@Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
	private UUID id;

	@Column(name = "user_id", columnDefinition = "uuid", nullable = false)
	private UUID userId;

	@Column(name = "refresh_token_hash", nullable = false)
	private String refreshTokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	private UserSession(UUID userId, String refreshTokenHash, Instant expiresAt, Instant createdAt) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.refreshTokenHash = refreshTokenHash;
		this.expiresAt = expiresAt;
		this.createdAt = createdAt;
	}

	public static UserSession issue(UUID userId, String refreshTokenHash, Instant expiresAt, Instant now) {
		return new UserSession(userId, refreshTokenHash, expiresAt, now);
	}

	public boolean isActive(Instant now) {
		return revokedAt == null && expiresAt.isAfter(now);
	}

	public void revoke(Instant now) {
		if (revokedAt == null) {
			this.revokedAt = now;
		}
	}
}
