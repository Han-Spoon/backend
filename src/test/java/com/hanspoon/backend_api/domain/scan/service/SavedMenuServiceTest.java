package com.hanspoon.backend_api.domain.scan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.domain.scan.dto.SaveMenuRequest;
import com.hanspoon.backend_api.domain.scan.entity.MenuAnalysis;
import com.hanspoon.backend_api.domain.scan.entity.SavedMenu;
import com.hanspoon.backend_api.domain.scan.entity.ScanSession;
import com.hanspoon.backend_api.domain.scan.entity.ScanStatus;
import com.hanspoon.backend_api.domain.scan.repository.MenuAnalysisRepository;
import com.hanspoon.backend_api.domain.scan.repository.SavedMenuRepository;
import com.hanspoon.backend_api.domain.scan.repository.ScanSessionRepository;
import com.hanspoon.backend_api.global.exception.BusinessException;
import com.hanspoon.backend_api.global.exception.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavedMenuServiceTest {
    @Mock SavedMenuRepository favorites;
    @Mock ScanSessionRepository scans;
    @Mock MenuAnalysisRepository analyses;
    @Mock ScanRecordService records;
    @InjectMocks SavedMenuService service;

    @Test
    void savesAnalyzedMenuUnderItsOwnScanAndKeepsRepeatedSavesIdempotent() {
        UUID userId = UUID.randomUUID();
        ScanSession scan = ScanSession.create(userId, null, 1, 0, ScanStatus.COMPLETED, Instant.now());
        MenuAnalysis menu = MenuAnalysis.create(scan.getId(), 0, "야채김밥", "Vegetable gimbap",
                null, null, "4000", false, null, RiskLevel.CAUTION, List.of("fish_cake"), null, null);
        SavedMenu saved = SavedMenu.create(userId, scan.getId(), 0, "야채김밥", RiskLevel.CAUTION, null, null);
        SaveMenuRequest request = new SaveMenuRequest(scan.getId(), 0);
        when(scans.findByIdAndUserIdForUpdate(scan.getId(), userId)).thenReturn(Optional.of(scan));
        when(analyses.findByScanSessionIdOrderByDisplayOrder(scan.getId())).thenReturn(List.of(menu));
        when(favorites.findByUserIdAndScanSessionIdAndDisplayOrder(userId, scan.getId(), 0))
                .thenReturn(Optional.empty(), Optional.of(saved));
        when(favorites.save(any(SavedMenu.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(scans.findByIdAndUserId(scan.getId(), userId)).thenReturn(Optional.of(scan));

        var first = service.save(userId, request);
        var second = service.save(userId, request);

        assertThat(first.menuName()).isEqualTo("야채김밥");
        assertThat(first.riskLevel()).isEqualTo(RiskLevel.CAUTION);
        assertThat(second.id()).isEqualTo(saved.getId());
        verify(favorites).save(any(SavedMenu.class));
    }

    @Test
    void refusesToSaveAnotherUsersScan() {
        UUID userId = UUID.randomUUID();
        UUID scanId = UUID.randomUUID();
        when(scans.findByIdAndUserIdForUpdate(scanId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(userId, new SaveMenuRequest(scanId, 0)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.SCAN_NOT_FOUND);
        verify(favorites, never()).save(any());
    }
}
