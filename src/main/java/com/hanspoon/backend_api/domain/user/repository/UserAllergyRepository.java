package com.hanspoon.backend_api.domain.user.repository;

import com.hanspoon.backend_api.domain.user.entity.UserAllergy;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAllergyRepository extends JpaRepository<UserAllergy, UUID> {

    List<UserAllergy> findByUserProfileId(UUID userProfileId);

    void deleteByUserProfileId(UUID userProfileId);
}
