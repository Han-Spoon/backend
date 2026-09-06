package com.hanspoon.backend_api.domain.upload.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "업로드 티켓 발급 응답")
public record UploadTicketResponse(String storageKey, String uploadUrl, Instant expiresAt) {}
