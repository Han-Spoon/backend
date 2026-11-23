package com.hanspoon.backend_api.domain.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * 후기 작성 요청.
 *
 * @param scanId 내 완료된 스캔, 스캔당 후기 1건
 * @param menus 메뉴 줄 1~30
 */
@Schema(description = "후기 작성 요청")
public record ReviewRequest(
        UUID scanId, @NotNull @Size(min = 1, max = 30) List<@Valid @NotNull ReviewMenuRequest> menus) {}
