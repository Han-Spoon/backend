package com.hanspoon.backend_api.domain.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 후기 신고 요청.
 *
 * @param reason inappropriate | false_info | spam | other
 * @param detail 선택 상세, 공백만이면 null
 */
@Schema(description = "후기 신고 요청")
public record ReviewReportRequest(
        @NotNull @Pattern(regexp = "inappropriate|false_info|spam|other") String reason,
        @Size(max = 500) String detail) {}
