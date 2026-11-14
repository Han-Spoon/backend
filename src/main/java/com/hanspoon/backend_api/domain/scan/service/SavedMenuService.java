package com.hanspoon.backend_api.domain.scan.service;

import com.hanspoon.backend_api.domain.scan.dto.SaveMenuRequest;
import com.hanspoon.backend_api.domain.scan.dto.SavedMenuResponse;
import com.hanspoon.backend_api.domain.scan.dto.ScanRecordResponse;
import com.hanspoon.backend_api.domain.scan.dto.ScanStoreSummary;
import com.hanspoon.backend_api.domain.scan.entity.MenuAnalysis;
import com.hanspoon.backend_api.domain.scan.entity.SavedMenu;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import com.hanspoon.backend_api.domain.scan.repository.MenuAnalysisRepository;
import com.hanspoon.backend_api.domain.scan.repository.SavedMenuRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SavedMenuService {
    private final SavedMenuRepository favorites;
    private final ScanSessionRepository scans;
    private final MenuAnalysisRepository analyses;
    private final ScanRecordService records;

    public SavedMenuService(
            SavedMenuRepository favorites,
            ScanSessionRepository scans,
            MenuAnalysisRepository analyses,
            ScanRecordService records) {
        this.favorites = favorites;
        this.scans = scans;
        this.analyses = analyses;
        this.records = records;
    }

    @Transactional(readOnly = true)
    public List<SavedMenuResponse> list(UUID userId) {
        return favorites.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SavedMenuResponse save(UUID userId, SaveMenuRequest request) {
        ScanSession scan = scans.findByIdAndUserIdForUpdate(request.scanId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCAN_NOT_FOUND));
        if (scan.getScanStatus() != ScanStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.SCAN_NOT_COMPLETED);
        }
        MenuAnalysis menu = analyses.findByScanSessionIdOrderByDisplayOrder(scan.getId()).stream()
                .filter(item -> request.displayOrder().equals(item.getDisplayOrder()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.MENU_NOT_FOUND));
        return favorites
                .findByUserIdAndScanSessionIdAndDisplayOrder(userId, scan.getId(), request.displayOrder())
                .map(this::toResponse)
                .orElseGet(() -> {
                    ScanRecordResponse record = records.findResponse(scan);
                    ScanStoreSummary store = record != null && record.store() != null
                            ? record.store()
                            : scan.getStoreId() == null
                                    ? null
                                    : new ScanStoreSummary(scan.getStoreId(), scan.getStoreNameSnapshot());
                    return SavedMenuResponse.from(favorites.save(SavedMenu.create(
                            userId,
                            scan.getId(),
                            request.displayOrder(),
                            menu.getMenuNameKo(),
                            menu.getRiskLevel(),
                            store == null ? null : store.storeId(),
                            store == null ? null : store.name())));
                });
    }

    private SavedMenuResponse toResponse(SavedMenu menu) {
        ScanSession scan = scans.findByIdAndUserId(menu.getScanSessionId(), menu.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SCAN_NOT_FOUND));
        ScanRecordResponse record = records.findResponse(scan);
        ScanStoreSummary store = record != null && record.store() != null
                ? record.store()
                : menu.getStoreId() == null ? null : new ScanStoreSummary(menu.getStoreId(), menu.getStoreName());
        return SavedMenuResponse.from(menu, store);
    }

    @Transactional
    public void delete(UUID userId, UUID favoriteId) {
        favorites.findByIdAndUserId(favoriteId, userId).ifPresent(favorites::delete);
    }
}
