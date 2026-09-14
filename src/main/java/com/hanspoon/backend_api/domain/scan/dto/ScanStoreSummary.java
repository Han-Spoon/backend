package com.hanspoon.backend_api.domain.scan.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 스캔 시점에 동결된 가게 정보. 가게명이 바뀌어도 과거 이력은 변하지 않는다. */
@Schema(description = "스캔 가게 정보")
public record ScanStoreSummary(
        @Schema(description = "가게 ID", example = "10342") Long storeId,
        @Schema(description = "스캔 시점 상호명", example = "한스푼") String name) {}
