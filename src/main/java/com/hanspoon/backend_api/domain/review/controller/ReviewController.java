package com.hanspoon.backend_api.domain.review.controller;

import com.hanspoon.backend_api.domain.review.dto.ReviewReportRequest;
import com.hanspoon.backend_api.domain.review.dto.ReviewRequest;
import com.hanspoon.backend_api.domain.review.dto.ReviewResponse;
import com.hanspoon.backend_api.domain.review.dto.ReviewUpdateRequest;
import com.hanspoon.backend_api.domain.review.service.ReviewService;
import com.hanspoon.backend_api.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Review", description = "식사 후기 API")
@RestController
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(summary = "가게 공개 후기 전체. 최신순, 숨김 제외, 익명")
    @GetMapping("/api/v1/stores/{storeId}/reviews")
    public List<ReviewResponse> listByStore(@CurrentUser String userId, @PathVariable long storeId) {
        return reviewService.listByStore(UUID.fromString(userId), storeId);
    }

    @Operation(summary = "내가 작성한 후기 전체. 최신순, 숨김 포함")
    @GetMapping("/api/v1/reviews/me")
    public List<ReviewResponse> listMine(@CurrentUser String userId) {
        return reviewService.listMine(UUID.fromString(userId));
    }

    @Operation(summary = "식사 후기 작성. 스캔당 1건, 식단 항목은 내 현재 프로필만")
    @PostMapping("/api/v1/stores/{storeId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse create(
            @CurrentUser String userId, @PathVariable long storeId, @Valid @RequestBody ReviewRequest request) {
        return reviewService.create(UUID.fromString(userId), storeId, request);
    }

    @Operation(summary = "식사 후기 수정. 메뉴 줄 전체 교체, 본인 후기만")
    @PatchMapping("/api/v1/reviews/{reviewId}")
    public ReviewResponse update(
            @CurrentUser String userId, @PathVariable UUID reviewId, @Valid @RequestBody ReviewUpdateRequest request) {
        return reviewService.update(UUID.fromString(userId), reviewId, request);
    }

    @Operation(summary = "식사 후기 삭제. 메뉴 줄·응답·신고 함께 삭제, 스캔 기록은 유지")
    @DeleteMapping("/api/v1/reviews/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser String userId, @PathVariable UUID reviewId) {
        reviewService.delete(UUID.fromString(userId), reviewId);
    }

    @Operation(summary = "남의 후기 신고. 저장만, 내 후기 400, 재신고 409")
    @PostMapping("/api/v1/reviews/{reviewId}/reports")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(
            @CurrentUser String userId, @PathVariable UUID reviewId, @Valid @RequestBody ReviewReportRequest request) {
        reviewService.report(UUID.fromString(userId), reviewId, request);
    }
}
