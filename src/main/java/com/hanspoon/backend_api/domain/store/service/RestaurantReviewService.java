package com.hanspoon.backend_api.domain.store.service;

import com.hanspoon.backend_api.domain.scan.dto.ScanRecordResponse;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.scan.service.ScanRecordService;
import com.hanspoon.backend_api.domain.store.dto.ReviewMenu;
import com.hanspoon.backend_api.domain.store.dto.ReviewReportRequest;
import com.hanspoon.backend_api.domain.store.dto.ReviewRequest;
import com.hanspoon.backend_api.domain.store.dto.ReviewResponse;
import com.hanspoon.backend_api.domain.store.entity.RestaurantReview;
import com.hanspoon.backend_api.domain.store.entity.RestaurantReviewReport;
import com.hanspoon.backend_api.domain.store.entity.Store;
import com.hanspoon.backend_api.domain.store.entity.StoreStatus;
import com.hanspoon.backend_api.domain.store.repository.RestaurantReviewReportRepository;
import com.hanspoon.backend_api.domain.store.repository.RestaurantReviewRepository;
import com.hanspoon.backend_api.domain.store.repository.StoreRepository;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RestaurantReviewService {
    private static final Set<String> ANSWERS = Set.of("yes", "no", "unknown");
    private final RestaurantReviewRepository reviews;
    private final RestaurantReviewReportRepository reports;
    private final StoreRepository stores;
    private final ScanSessionRepository scans;
    private final ScanRecordService scanRecords;

    public RestaurantReviewService(
            RestaurantReviewRepository reviews,
            RestaurantReviewReportRepository reports,
            StoreRepository stores,
            ScanSessionRepository scans,
            ScanRecordService scanRecords) {
        this.reviews = reviews;
        this.reports = reports;
        this.stores = stores;
        this.scans = scans;
        this.scanRecords = scanRecords;
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> forStore(UUID viewerId, Long storeId) {
        return reviews.findByStoreIdOrderByCreatedAtDesc(storeId).stream()
                .map(review -> response(review, viewerId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> mine(UUID userId) {
        return reviews.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(review -> response(review, userId))
                .toList();
    }

    @Transactional
    public ReviewResponse create(UUID userId, Long storeId, ReviewRequest request) {
        stores.findByIdAndStatus(storeId, StoreStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
        validateMenus(request.menus());
        validateScan(userId, storeId, request.scanId());
        return response(
                reviews.save(RestaurantReview.create(userId, storeId, request.scanId(), request.menus())), userId);
    }

    @Transactional
    public ReviewResponse update(UUID userId, UUID reviewId, ReviewRequest request) {
        RestaurantReview review = reviews.findByIdAndUserId(reviewId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        validateMenus(request.menus());
        if (request.scanId() != null && !request.scanId().equals(review.getScanSessionId())) {
            throw new BusinessException(ErrorCode.REVIEW_STORE_MISMATCH);
        }
        review.updateMenus(request.menus());
        return response(review, userId);
    }

    @Transactional
    public void delete(UUID userId, UUID reviewId) {
        RestaurantReview review = reviews.findByIdAndUserId(reviewId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        reviews.delete(review);
    }

    @Transactional
    public void report(UUID userId, UUID reviewId, ReviewReportRequest request) {
        RestaurantReview review =
                reviews.findById(reviewId).orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        if (review.getUserId().equals(userId)) throw new BusinessException(ErrorCode.REVIEW_SELF_REPORT);
        if (!reports.existsByReviewIdAndReporterId(reviewId, userId)) {
            reports.save(RestaurantReviewReport.create(reviewId, userId, request.reason()));
        }
    }

    private void validateScan(UUID userId, Long storeId, UUID scanId) {
        if (scanId == null) return;
        ScanSession scan = scans.findByIdAndUserId(scanId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCAN_NOT_FOUND));
        if (scan.getScanStatus() != ScanStatus.COMPLETED) throw new BusinessException(ErrorCode.SCAN_NOT_COMPLETED);
        ScanRecordResponse saved = scanRecords.findResponse(scan);
        Long linkedStore =
                saved != null && saved.store() != null ? saved.store().storeId() : scan.getStoreId();
        if (!storeId.equals(linkedStore)) throw new BusinessException(ErrorCode.REVIEW_STORE_MISMATCH);
    }

    private static void validateMenus(List<ReviewMenu> menus) {
        Set<String> names = new HashSet<>();
        for (ReviewMenu menu : menus) {
            if (menu == null
                    || menu.name() == null
                    || menu.name().isBlank()
                    || menu.name().length() > 200
                    || !names.add(menu.name().trim())
                    || menu.answers() == null
                    || menu.answers().size() > 20
                    || menu.answers().entrySet().stream()
                            .anyMatch(entry -> entry.getKey() == null
                                    || entry.getKey().isBlank()
                                    || entry.getKey().length() > 80
                                    || !ANSWERS.contains(entry.getValue()))
                    || (menu.note() != null && menu.note().length() > 500)) {
                throw new BusinessException(ErrorCode.REVIEW_INVALID_CONTENT);
            }
        }
    }

    private ReviewResponse response(RestaurantReview review, UUID viewerId) {
        String storeName =
                stores.findById(review.getStoreId()).map(Store::getName).orElse("");
        return new ReviewResponse(
                review.getId(),
                review.getStoreId(),
                storeName,
                review.getScanSessionId(),
                review.getMenus(),
                review.getUserId().equals(viewerId),
                review.getCreatedAt(),
                review.getUpdatedAt());
    }
}
