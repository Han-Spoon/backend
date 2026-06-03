package com.hanspoon.backend_api.domain.auth.repository;

import com.hanspoon.backend_api.domain.auth.entity.AuthProvider;
import com.hanspoon.backend_api.domain.auth.entity.UserAuthIdentity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAuthIdentityRepository extends JpaRepository<UserAuthIdentity, UUID> {

    Optional<UserAuthIdentity> findByProviderAndProviderId(AuthProvider provider, String providerId);
}
