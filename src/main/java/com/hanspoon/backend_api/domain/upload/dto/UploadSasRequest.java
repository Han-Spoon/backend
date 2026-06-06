package com.hanspoon.backend_api.domain.upload.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 쓰기 SAS 발급 요청 (Path B).
 *
 * @param contentType 업로드할 이미지 MIME 타입 (예: image/jpeg)
 */
@Schema(description = "업로드 SAS 발급 요청")
public record UploadSasRequest(
        @Schema(description = "이미지 MIME 타입", example = "image/jpeg") @NotBlank String contentType) {}
