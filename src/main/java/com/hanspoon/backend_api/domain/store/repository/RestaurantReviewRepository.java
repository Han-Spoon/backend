package com.hanspoon.backend_api.domain.store.repository;

import com.hanspoon.backend_api.domain.store.entity.RestaurantReview;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantReviewRepository extends JpaRepository<RestaurantReview, UUID> {
    List<RestaurantReview> findByStoreIdOrderByCreatedAtDesc(Long storeId);

    List<RestaurantReview> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<RestaurantReview> findByIdAndUserId(UUID id, UUID userId);

    long countByStoreId(Long storeId);
}
