package com.hanspoon.backend_api.domain.store.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        Long storeId,
        String storeName,
        UUID scanId,
        List<ReviewMenu> menus,
        boolean mine,
        Instant createdAt,
        Instant updatedAt) {}
