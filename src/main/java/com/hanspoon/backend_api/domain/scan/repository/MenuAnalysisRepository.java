package com.hanspoon.backend_api.domain.scan.repository;

import com.hanspoon.backend_api.domain.scan.entity.MenuAnalysis;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuAnalysisRepository extends JpaRepository<MenuAnalysis, UUID> {

    List<MenuAnalysis> findByScanSessionIdOrderByDisplayOrder(UUID scanSessionId);
}
