package com.hanspoon.backend_api.domain.scan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 스캔 이력 제목 수정 요청.
 *
 * @param title 새 제목(1~30자, 공백 불가)
 */
@Schema(description = "스캔 이력 제목 수정 요청")
public record UpdateScanTitleRequest(
        @Schema(description = "새 제목", example = "강남 삼겹살집")
                @NotBlank @Size(min = 1, max = 30, message = "title length must be between 1 and 30.") String title) {}
