package com.hanspoon.backend_api.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 프로필별 알레르기 1건(불변 row). user_profiles 와 N:1 (FK ON DELETE CASCADE).
 * 수정 시에는 user_profile_id 기준으로 전체 삭제 후 재삽입한다.
 */
@Entity
@Table(name = "user_allergies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAllergy {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_profile_id", columnDefinition = "uuid", nullable = false)
    private UUID userProfileId;

    @Column(name = "allergy_code", length = 50, nullable = false)
    private AllergyCode allergyCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private UserAllergy(UUID userProfileId, AllergyCode allergyCode, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.userProfileId = userProfileId;
        this.allergyCode = allergyCode;
        this.createdAt = createdAt;
    }

    public static UserAllergy of(UUID userProfileId, AllergyCode allergyCode) {
        return new UserAllergy(userProfileId, allergyCode, Instant.now());
    }
}
