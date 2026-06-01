package com.hanspoon.backend_api.domain.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hanspoon.backend_api.domain.auth.entity.UserSession;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

	Optional<UserSession> findByRefreshTokenHash(String refreshTokenHash);

	List<UserSession> findAllByUserIdAndRevokedAtIsNull(UUID userId);
}
