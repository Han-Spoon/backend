package com.hanspoon.backend_api.domain.store.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.domain.scan.service.ScanRecordService;
import com.hanspoon.backend_api.domain.store.dto.ReviewMenu;
import com.hanspoon.backend_api.domain.store.dto.ReviewReportRequest;
import com.hanspoon.backend_api.domain.store.dto.ReviewRequest;
import com.hanspoon.backend_api.domain.store.entity.RestaurantReview;
import com.hanspoon.backend_api.domain.store.entity.RestaurantReviewReport;
import com.hanspoon.backend_api.domain.store.repository.RestaurantReviewReportRepository;
import com.hanspoon.backend_api.domain.store.repository.RestaurantReviewRepository;
import com.hanspoon.backend_api.domain.store.repository.StoreRepository;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RestaurantReviewServiceTest {
    @Mock
    RestaurantReviewRepository reviews;

    @Mock
    RestaurantReviewReportRepository reports;

    @Mock
    StoreRepository stores;

    @Mock
    ScanSessionRepository scans;

    @Mock
    ScanRecordService scanRecords;

    @InjectMocks
    RestaurantReviewService service;

    @Test
    void anotherUserCannotEditReview() {
        UUID viewer = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        when(reviews.findByIdAndUserId(reviewId, viewer)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(
                        viewer, reviewId, new ReviewRequest(null, List.of(new ReviewMenu("김밥", Map.of(), "좋았어요")))))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.REVIEW_NOT_FOUND);
        verify(reviews, never()).save(any());
    }

    @Test
    void reportingLeavesReviewVisibleForModeration() {
        UUID author = UUID.randomUUID();
        UUID reporter = UUID.randomUUID();
        RestaurantReview review =
                RestaurantReview.create(author, 1L, null, List.of(new ReviewMenu("김밥", Map.of(), null)));
        when(reviews.findById(review.getId())).thenReturn(Optional.of(review));
        when(reports.existsByReviewIdAndReporterId(review.getId(), reporter)).thenReturn(false);

        service.report(reporter, review.getId(), new ReviewReportRequest(ReviewReportRequest.Reason.AD_SPAM));

        verify(reports).save(any(RestaurantReviewReport.class));
        verify(reviews, never()).delete(any());
    }

    @Test
    void duplicateMenuNamesAreRejected() {
        UUID viewer = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        RestaurantReview review =
                RestaurantReview.create(viewer, 1L, null, List.of(new ReviewMenu("김밥", Map.of(), null)));
        when(reviews.findByIdAndUserId(reviewId, viewer)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.update(
                        viewer,
                        reviewId,
                        new ReviewRequest(
                                null,
                                List.of(new ReviewMenu("김밥", Map.of(), null), new ReviewMenu("김밥", Map.of(), null)))))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.REVIEW_INVALID_CONTENT);
    }
}
