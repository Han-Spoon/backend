package com.hanspoon.backend_api.domain.auth.entity;

import com.hanspoon.backend_api.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_auth_identities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAuthIdentity extends BaseEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
    private UUID userId;

    @Column(name = "provider", length = 10, nullable = false)
    private AuthProvider provider;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    private UserAuthIdentity(UUID userId, AuthProvider provider, String providerId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.provider = provider;
        this.providerId = providerId;
    }

    public static UserAuthIdentity google(UUID userId, String providerId) {
        return new UserAuthIdentity(userId, AuthProvider.GOOGLE, providerId);
    }
}
