package com.hanspoon.backend_api.domain.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

/**
 * 후기 메뉴 줄.
 *
 * @param name 메뉴 이름, 후기 안에서 정규화 이름 중복 금지
 * @param scanMenuId 스캔 메뉴에서 고른 줄, scanId 의 메뉴만
 * @param answers 식단 코드 → present | absent | unknown, 내 프로필 항목만
 * @param comment 더 남기고 싶은 말, 공백만이면 null
 */
@Schema(description = "후기 메뉴 줄")
public record ReviewMenuRequest(
        @NotBlank @Size(max = 255) String name,
        UUID scanMenuId,
        Map<String, String> answers,
        @Size(max = 500) String comment) {}
