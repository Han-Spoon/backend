package com.hanspoon.backend_api.domain.scan.repository;

import com.hanspoon.backend_api.domain.scan.entity.SavedMenu;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedMenuRepository extends JpaRepository<SavedMenu, UUID> {
    List<SavedMenu> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<SavedMenu> findByUserIdAndScanSessionIdAndDisplayOrder(
            UUID userId, UUID scanSessionId, Integer displayOrder);

    Optional<SavedMenu> findByIdAndUserId(UUID id, UUID userId);
}
