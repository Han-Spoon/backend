package com.hanspoon.backend_api.domain.scan.repository;

import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanSessionRepository extends JpaRepository<ScanSession, UUID> {

    Page<ScanSession> findByUserId(UUID userId, Pageable pageable);

    Optional<ScanSession> findByIdAndUserId(UUID id, UUID userId);
}
