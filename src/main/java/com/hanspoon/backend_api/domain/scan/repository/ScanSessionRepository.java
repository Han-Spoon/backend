package com.hanspoon.backend_api.domain.scan.repository;

import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanSessionRepository extends JpaRepository<ScanSession, UUID> {

    List<ScanSession> findByUserId(UUID userId);

    Optional<ScanSession> findByIdAndUserId(UUID id, UUID userId);
}
