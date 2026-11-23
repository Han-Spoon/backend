package com.hanspoon.backend_api.domain.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 후기 수정 요청. 메뉴 줄 전체 교체.
 *
 * @param menus 메뉴 줄 1~30, 작성과 같은 규칙
 */
@Schema(description = "후기 수정 요청")
public record ReviewUpdateRequest(@NotNull @Size(min = 1, max = 30) List<@Valid @NotNull ReviewMenuRequest> menus) {}
