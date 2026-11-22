package com.hanspoon.backend_api.domain.scan.repository;

import com.hanspoon.backend_api.domain.scan.entity.ScanAiResult;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanAiResultRepository extends JpaRepository<ScanAiResult, UUID> {}
