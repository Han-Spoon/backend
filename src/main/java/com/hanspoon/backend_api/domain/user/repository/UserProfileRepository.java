package com.hanspoon.backend_api.domain.user.repository;

import com.hanspoon.backend_api.domain.user.entity.UserProfile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    boolean existsByUserId(UUID userId);
}
