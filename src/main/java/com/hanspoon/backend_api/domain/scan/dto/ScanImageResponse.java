package com.hanspoon.backend_api.domain.scan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** 원본 메뉴판 사진 읽기 URL. */
public record ScanImageResponse(
        @Schema(description = "읽기 URL. 만료되면 다시 조회") String imageUrl,
        @Schema(description = "image/jpeg | image/png | image/webp", example = "image/jpeg") String contentType,
        @Schema(description = "URL 만료 시각") Instant expiresAt,
        @Schema(description = "사진 보관 만료 시각(업로드 + 90일)") Instant availableUntil) {}
