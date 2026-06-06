package com.hanspoon.backend_api.domain.scan.dto;

import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * 스캔 시작 응답(202). 실제 OCR/판정은 비동기로 진행되며, 결과는 GET /api/v1/scans/{scanId} 로 폴링한다.
 *
 * @param scanId 생성된 스캔 세션 id
 * @param status 현재 상태 (생성 직후 processing)
 */
@Schema(description = "스캔 시작 응답")
public record ScanCreatedResponse(UUID scanId, ScanStatus status) {}
