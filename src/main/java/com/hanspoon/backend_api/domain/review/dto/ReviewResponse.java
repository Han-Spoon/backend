package com.hanspoon.backend_api.domain.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 식사 후기 1건. 작성자 정보 없음(익명).
 *
 * @param reviewId 수정·삭제·신고 경로 값
 * @param storeId 가게
 * @param storeName 지금 가게 이름
 * @param scanId 스캔으로 쓴 후기의 스캔, 남의 후기면 null
 * @param menus 메뉴 줄, 작성 순
 * @param mine 내 후기 여부
 * @param hidden 관리자 숨김 여부
 */
@Schema(description = "식사 후기")
public record ReviewResponse(
        UUID reviewId,
        long storeId,
        String storeName,
        UUID scanId,
        List<Menu> menus,
        boolean mine,
        boolean hidden,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * @param scanMenuId 스캔 메뉴에서 고른 줄, 남의 후기면 null
     * @param commentLocale comment 원문 언어
     */
    public record Menu(
            String name, UUID scanMenuId, Map<String, String> answers, String comment, String commentLocale) {}
}
