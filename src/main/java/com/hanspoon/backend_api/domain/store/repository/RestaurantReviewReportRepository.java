package com.hanspoon.backend_api.domain.store.repository;

import com.hanspoon.backend_api.domain.store.entity.RestaurantReviewReport;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantReviewReportRepository extends JpaRepository<RestaurantReviewReport, UUID> {
    boolean existsByReviewIdAndReporterId(UUID reviewId, UUID reporterId);
}
