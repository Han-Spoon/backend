package com.hanspoon.backend_api.domain.upload.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

@Schema(description = "업로드 티켓 발급 응답")
public record UploadTicketResponse(
        String storageKey,
        String uploadUrl,
        Instant expiresAt,
        @Schema(description = "Presigned PUT 요청에 그대로 포함해야 하는 서명된 헤더") Map<String, String> uploadHeaders) {}
