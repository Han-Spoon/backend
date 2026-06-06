package com.hanspoon.backend_api.domain.scan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 스캔 이력 제목 수정 요청.
 *
 * @param title 새 제목(1~255자, 공백 불가)
 */
@Schema(description = "스캔 이력 제목 수정 요청")
public record UpdateScanTitleRequest(
        @Schema(description = "새 제목", example = "강남 삼겹살집")
                @NotBlank @Size(min = 1, max = 255, message = "title length must be between 1 and 255.") String title) {}
