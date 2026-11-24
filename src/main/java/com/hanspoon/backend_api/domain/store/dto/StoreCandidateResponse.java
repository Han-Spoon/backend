package com.hanspoon.backend_api.domain.store.dto;

import com.hanspoon.backend_api.domain.store.entity.StoreMatchMethod;
import io.swagger.v3.oas.annotations.media.Schema;

/** 사용자가 선택할 수 있는 가게 후보. 내부 검색 점수는 API 계약에 노출하지 않는다. */
@Schema(description = "가게 후보")
public record StoreCandidateResponse(
        @Schema(description = "가게 ID", example = "10342") Long storeId,
        @Schema(description = "상호명", example = "한스푼") String name,
        @Schema(description = "지점명", example = "강남점") String branchName,
        @Schema(description = "도로명 주소", example = "서울특별시 강남구 테헤란로 1") String roadAddress,
        @Schema(description = "층 정보. 지상 1, 지하 B1, 미상은 빈 문자열", example = "B1") String floorInfo,
        @Schema(description = "가게 위도", example = "37.4978") double latitude,
        @Schema(description = "가게 경도", example = "127.0275") double longitude,
        @Schema(description = "사용자 위치와의 거리(m). 좌표 없이 검색하면 null", example = "42") Integer distanceMeters,
        @Schema(description = "상권업종 소분류 코드", example = "I20101") String categoryCode,
        @Schema(description = "상권업종 소분류명", example = "한식 일반 음식점업") String categoryName,
        @Schema(description = "공개 후기 수(숨김 제외). 정렬에는 미사용", example = "3") int reviewCount,
        @Schema(description = "서비스 검증 완료 여부", example = "true") boolean verified,
        @Schema(description = "후보 검색 경로", example = "gps_candidate") StoreMatchMethod matchMethod,
        @Schema(description = "대표 사진 주소. 가게 사진, 없으면 사진 있는 첫 메뉴, 없으면 null") String thumbnailUrl,
        @Schema(description = "메뉴명으로 찾은 가게의 걸린 메뉴 표시명. 이름 매칭·반경 검색은 null", example = "참치김밥") String matchedMenuName) {}
