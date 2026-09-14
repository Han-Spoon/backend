package com.hanspoon.backend_api.domain.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 사용자 위치 주변의 가게 후보 검색 조건. 위치값은 검색에만 사용하고 저장하지 않는다. */
@Schema(description = "가게 후보 검색 조건")
public record StoreCandidateSearchRequest(
        @Schema(description = "사용자 현재 위도", example = "37.4979")
                @NotNull @DecimalMin(value = "33.0", message = "latitude must be at least 33.0") @DecimalMax(value = "39.0", message = "latitude must be at most 39.0") Double latitude,
        @Schema(description = "사용자 현재 경도", example = "127.0276")
                @NotNull @DecimalMin(value = "124.0", message = "longitude must be at least 124.0") @DecimalMax(value = "132.0", message = "longitude must be at most 132.0") Double longitude,
        @Schema(description = "선택적 상호명 검색어", example = "한스푼") @Size(max = 100) String query,
        @Schema(description = "검색 반경(m), 기본 100", example = "100")
                @Min(value = 10, message = "radiusMeters must be at least 10") @Max(value = 1000, message = "radiusMeters must be at most 1000") Integer radiusMeters,
        @Schema(description = "최대 후보 수, 기본·최대 20", example = "20")
                @Min(value = 1, message = "limit must be at least 1") @Max(value = 20, message = "limit must be at most 20") Integer limit) {

    private static final int DEFAULT_RADIUS_METERS = 100;
    private static final int DEFAULT_LIMIT = 20;

    public int resolvedRadiusMeters() {
        return radiusMeters == null ? DEFAULT_RADIUS_METERS : radiusMeters;
    }

    public int resolvedLimit() {
        return limit == null ? DEFAULT_LIMIT : limit;
    }

    public String trimmedQuery() {
        return query == null || query.isBlank() ? null : query.strip();
    }
}
