package com.hanspoon.backend_api.domain.scan.dto;

import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.domain.scan.entity.SavedMenu;
import java.time.Instant;
import java.util.UUID;

public record SavedMenuResponse(UUID id, UUID scanId, Integer displayOrder, String menuName,
        RiskLevel riskLevel, ScanStoreSummary store, Instant savedAt) {
    public static SavedMenuResponse from(SavedMenu menu) {
        return from(menu, menu.getStoreId() == null ? null : new ScanStoreSummary(menu.getStoreId(), menu.getStoreName()));
    }

    public static SavedMenuResponse from(SavedMenu menu, ScanStoreSummary store) {
        return new SavedMenuResponse(menu.getId(), menu.getScanSessionId(), menu.getDisplayOrder(),
                menu.getMenuName(), menu.getRiskLevel(), store, menu.getCreatedAt());
    }
}
