package com.hanspoon.backend_api.domain.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 사용자가 선택할 수 있는 가게 후보. 내부 검색 점수는 API 계약에 노출하지 않는다. */
@Schema(description = "가게 후보")
public record StoreCandidateResponse(
        @Schema(description = "가게 ID", example = "10342") Long storeId,
        @Schema(description = "상호명", example = "한스푼") String name,
        @Schema(description = "지점명", example = "강남점") String branchName,
        @Schema(description = "도로명 주소", example = "서울특별시 강남구 테헤란로 1") String roadAddress,
        @Schema(description = "가게 위도", example = "37.4978") double latitude,
        @Schema(description = "가게 경도", example = "127.0275") double longitude,
        @Schema(description = "사용자 위치와의 거리(m)", example = "42") int distanceMeters,
        @Schema(description = "상권업종 소분류 코드", example = "I20101") String categoryCode,
        @Schema(description = "상권업종 소분류명", example = "한식 일반 음식점업") String categoryName,
        @Schema(description = "서비스 검증 완료 여부", example = "true") boolean verified) {}
