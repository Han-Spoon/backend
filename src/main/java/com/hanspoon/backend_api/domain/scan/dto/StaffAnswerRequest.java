package com.hanspoon.backend_api.domain.scan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 직원 응답 저장 요청.
 *
 * @param answer 직원 답 (present | absent | unknown)
 */
@Schema(description = "직원 응답 저장 요청")
public record StaffAnswerRequest(
        @Schema(description = "직원 답", example = "present")
                @NotNull @Pattern(regexp = "present|absent|unknown", message = "answer must be present, absent or unknown") String answer) {}
