package com.hanspoon.backend_api.domain.store.controller;

import com.hanspoon.backend_api.domain.store.dto.ReviewReportRequest;
import com.hanspoon.backend_api.domain.store.dto.ReviewRequest;
import com.hanspoon.backend_api.domain.store.dto.ReviewResponse;
import com.hanspoon.backend_api.domain.store.service.RestaurantReviewService;
import com.hanspoon.backend_api.global.security.CurrentUser;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RestaurantReviewController {
    private final RestaurantReviewService service;

    public RestaurantReviewController(RestaurantReviewService service) {
        this.service = service;
    }

    @GetMapping("/stores/{storeId}/reviews")
    public List<ReviewResponse> forStore(@CurrentUser String userId, @PathVariable Long storeId) {
        return service.forStore(UUID.fromString(userId), storeId);
    }

    @GetMapping("/reviews/me")
    public List<ReviewResponse> mine(@CurrentUser String userId) {
        return service.mine(UUID.fromString(userId));
    }

    @PostMapping("/stores/{storeId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse create(
            @CurrentUser String userId, @PathVariable Long storeId, @Valid @RequestBody ReviewRequest request) {
        return service.create(UUID.fromString(userId), storeId, request);
    }

    @PatchMapping("/reviews/{reviewId}")
    public ReviewResponse update(
            @CurrentUser String userId, @PathVariable UUID reviewId, @Valid @RequestBody ReviewRequest request) {
        return service.update(UUID.fromString(userId), reviewId, request);
    }

    @DeleteMapping("/reviews/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser String userId, @PathVariable UUID reviewId) {
        service.delete(UUID.fromString(userId), reviewId);
    }

    @PostMapping("/reviews/{reviewId}/reports")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(
            @CurrentUser String userId, @PathVariable UUID reviewId, @Valid @RequestBody ReviewReportRequest request) {
        service.report(UUID.fromString(userId), reviewId, request);
    }
}
