package com.hanspoon.backend_api.domain.user.entity;

import java.util.UUID;

import com.hanspoon.backend_api.global.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 최소 매핑. 현재는 로그인 응답의 hasProfile 판정(existsByUserId)에만 사용한다.
 * 국적/식단 등 나머지 컬럼은 온보딩 구현 시 매핑을 확장한다(테이블에는 이미 존재).
 */
@Entity
@Table(name = "user_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile extends BaseEntity {

	@Id
	@Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
	private UUID id;

	@Column(name = "user_id", columnDefinition = "uuid", nullable = false, unique = true)
	private UUID userId;
}
