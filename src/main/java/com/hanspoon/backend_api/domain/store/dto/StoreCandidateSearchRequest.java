package com.hanspoon.backend_api.domain.store.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 가게 후보 검색 조건. 위치값은 검색에만 사용하고 저장하지 않는다.
 *
 * <p>좌표만: 반경 검색. 좌표 + query: 반경 안 이름 검색. query 만: 전체 이름 검색.
 */
@Schema(description = "가게 후보 검색 조건")
public record StoreCandidateSearchRequest(
        @Schema(description = "사용자 현재 위도(query 만 있으면 생략 가능)", example = "37.4979")
                @DecimalMin(value = "33.0", message = "latitude must be at least 33.0") @DecimalMax(value = "39.0", message = "latitude must be at most 39.0") Double latitude,
        @Schema(description = "사용자 현재 경도(query 만 있으면 생략 가능)", example = "127.0276")
                @DecimalMin(value = "124.0", message = "longitude must be at least 124.0") @DecimalMax(value = "132.0", message = "longitude must be at most 132.0") Double longitude,
        @Schema(description = "상호명 검색어(좌표 없으면 필수)", example = "한스푼") @Size(max = 100) String query,
        @Schema(description = "검색 반경(m), 기본 100·최대 3000. 좌표 있을 때만", example = "100")
                @Min(value = 10, message = "radiusMeters must be at least 10") @Max(value = 3000, message = "radiusMeters must be at most 3000") Integer radiusMeters,
        @Schema(description = "최대 후보 수, 기본 20·최대 100", example = "20")
                @Min(value = 1, message = "limit must be at least 1") @Max(value = 100, message = "limit must be at most 100") Integer limit) {

    private static final int DEFAULT_RADIUS_METERS = 100;
    private static final int DEFAULT_LIMIT = 20;

    @AssertTrue(message = "latitude and longitude must be provided together") @JsonIgnore
    public boolean isCoordinatesComplete() {
        return (latitude == null) == (longitude == null);
    }

    @AssertTrue(message = "coordinates or query is required") @JsonIgnore
    public boolean isSearchConditionPresent() {
        return hasCoordinates() || trimmedQuery() != null;
    }

    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }

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
