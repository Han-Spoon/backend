package com.hanspoon.backend_api.domain.upload.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "업로드 티켓 발급 요청")
public record UploadTicketRequest(
        @Schema(description = "이미지 MIME 타입", example = "image/jpeg") @NotBlank String contentType) {}
