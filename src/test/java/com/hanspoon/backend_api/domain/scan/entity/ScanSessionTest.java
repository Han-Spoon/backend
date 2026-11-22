package com.hanspoon.backend_api.domain.scan.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.domain.store.entity.StoreMatchMethod;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScanSessionTest {

    @Test
    void terminalFailureCannotBeOverwrittenByALateWorker() {
        ScanSession session = ScanSession.startLegacy(UUID.randomUUID(), "scans/user/menu.jpg");
        session.markFailed("SCAN_PROCESSING_TIMEOUT");

        assertThatThrownBy(() -> session.applyJudgeResult(List.of(), null)).isInstanceOf(IllegalStateException.class);
        session.markFailed("INTERNAL_SERVER_ERROR");

        assertThat(session.getScanStatus()).isEqualTo(ScanStatus.FAILED);
        assertThat(session.getFailureCode()).isEqualTo("SCAN_PROCESSING_TIMEOUT");
    }

    @Test
    void countsRiskLevelsAndSkipsMenusWithoutVerdict() {
        ScanSession session = ScanSession.startLegacy(UUID.randomUUID(), "scans/user/menu.jpg");

        session.applyJudgeResult(Arrays.asList(RiskLevel.SAFE, RiskLevel.CAUTION, null, RiskLevel.CAUTION), null);

        assertThat(session.getDangerCount()).isZero();
        assertThat(session.getCautionCount()).isEqualTo(2);
        assertThat(session.getSafeCount()).isEqualTo(1);
    }

    @Test
    void riskCountsCannotBeWrittenAfterTerminalState() {
        ScanSession session = ScanSession.startLegacy(UUID.randomUUID(), "scans/user/menu.jpg");
        session.markFailed("SCAN_PROCESSING_TIMEOUT");

        assertThatThrownBy(() -> session.applyJudgeResult(Arrays.asList(RiskLevel.DANGER), null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startsWithAtomicStoreContext() {
        ScanSession session =
                ScanSession.start(UUID.randomUUID(), "scans/user/menu.jpg", 42L, "한스푼", StoreMatchMethod.NAME_SEARCH);

        assertThat(session.getStoreId()).isEqualTo(42L);
        assertThat(session.getStoreNameSnapshot()).isEqualTo("한스푼");
        assertThat(session.getStoreMatchMethod()).isEqualTo(StoreMatchMethod.NAME_SEARCH);
        assertThat(session.hasSameStoreContext(42L, StoreMatchMethod.NAME_SEARCH))
                .isTrue();
        assertThat(session.hasSameStoreContext(43L, StoreMatchMethod.NAME_SEARCH))
                .isFalse();
    }
}
