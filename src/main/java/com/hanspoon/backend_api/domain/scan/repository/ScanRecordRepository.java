package com.hanspoon.backend_api.domain.scan.repository;

import com.hanspoon.backend_api.domain.scan.entity.ScanRecord;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanRecordRepository extends JpaRepository<ScanRecord, UUID> {}
