package com.hanspoon.backend_api.domain.store.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** 가게 상세. 상단 정보 + 메뉴 탭. 후기는 별도 API. */
@Schema(description = "가게 상세")
public record StoreDetailResponse(
        @Schema(description = "가게 ID", example = "220752") Long storeId,
        @Schema(description = "상호명", example = "이가뚝배기") String name,
        @Schema(description = "지점명", example = "강남점") String branchName,
        @Schema(description = "도로명 주소", example = "서울특별시 용산구 원효로19길 56") String roadAddress,
        @Schema(description = "층 정보. 지상 1, 지하 B1, 미상은 빈 문자열", example = "1") String floorInfo,
        @Schema(description = "가게 위도", example = "37.53") double latitude,
        @Schema(description = "가게 경도", example = "126.96") double longitude,
        @Schema(description = "상권업종 소분류 코드", example = "I20102") String categoryCode,
        @Schema(description = "상권업종 소분류명", example = "국/탕/찌개류") String categoryName,
        @Schema(description = "서비스 검증 완료 여부", example = "true") boolean verified,
        @Schema(description = "공개 후기 수(숨김 제외)", example = "3") int reviewCount,
        @Schema(description = "대표 사진 주소. 가게 사진, 없으면 사진 있는 첫 메뉴, 없으면 null") String thumbnailUrl,
        @Schema(description = "메뉴, 등록 순") List<Menu> menus) {

    /** 가게 메뉴 1건. 이미지: 가게 메뉴 사진, 없으면 표준 메뉴 대표 이미지. */
    @Schema(name = "StoreDetailMenu", description = "가게 메뉴")
    public record Menu(
            @Schema(description = "가게 메뉴 ID", example = "9012") Long storeMenuId,
            @Schema(description = "메뉴판 표기", example = "뚝배기 불고기") String name,
            @Schema(description = "표준 메뉴의 사용자 언어 이름. 미연결·한국어 사용자면 null") String translatedName,
            @Schema(description = "메뉴 설명") String description,
            @Schema(description = "가격 문자열", example = "12,000원") String priceText,
            @Schema(description = "가격 옵션 배열") JsonNode priceOptions,
            @Schema(description = "연결 표준 메뉴 ID, 미연결이면 null", example = "37") Long menuId,
            @Schema(description = "메뉴 사진 주소, null 가능") String imageUrl,
            @Schema(description = "사진 출처 표기, imageUrl 과 함께 null") String imageAttribution,
            @Schema(description = "사진 출처 구분(store | standard), imageUrl 과 함께 null", example = "store")
                    String imageSource) {}
}
