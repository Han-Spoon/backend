package com.hanspoon.backend_api.domain.scan.repository;

import com.hanspoon.backend_api.domain.scan.entity.MenuImage;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuImageRepository extends JpaRepository<MenuImage, UUID> {

    Optional<MenuImage> findByScanSessionId(UUID scanSessionId);
}
