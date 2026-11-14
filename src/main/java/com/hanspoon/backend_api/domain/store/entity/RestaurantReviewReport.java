package com.hanspoon.backend_api.domain.store.entity;

import com.hanspoon.backend_api.domain.store.dto.ReviewReportRequest.Reason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "restaurant_review_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RestaurantReviewReport {
    @Id
    private UUID id;

    @Column(name = "review_id", nullable = false)
    private UUID reviewId;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    private Reason reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private RestaurantReviewReport(UUID reviewId, UUID reporterId, Reason reason) {
        this.id = UUID.randomUUID();
        this.reviewId = reviewId;
        this.reporterId = reporterId;
        this.reason = reason;
        this.createdAt = Instant.now();
    }

    public static RestaurantReviewReport create(UUID reviewId, UUID reporterId, Reason reason) {
        return new RestaurantReviewReport(reviewId, reporterId, reason);
    }
}
