package com.hanspoon.backend_api.domain.favorite.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** 메뉴 찜 요청. */
@Schema(description = "메뉴 찜 요청")
public record SaveMenuRequest(@Schema(description = "결과의 menus[].scanMenuId, 내 스캔의 메뉴만") @NotNull UUID scanMenuId) {}
