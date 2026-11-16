package com.hanspoon.backend_api.domain.scan.repository;

import com.hanspoon.backend_api.domain.scan.entity.ScanImage;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanImageRepository extends JpaRepository<ScanImage, UUID> {

    Optional<ScanImage> findByScanSessionId(UUID scanSessionId);
}
