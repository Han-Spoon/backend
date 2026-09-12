package com.hanspoon.backend_api.domain.scan.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScanSessionTest {

    @Test
    void terminalFailureCannotBeOverwrittenByALateWorker() {
        ScanSession session = ScanSession.start(UUID.randomUUID(), "scans/user/menu.jpg");
        session.markFailed("SCAN_PROCESSING_TIMEOUT");

        assertThatThrownBy(() -> session.applyRuleEngineResult(1, ScanStatus.COMPLETED))
                .isInstanceOf(IllegalStateException.class);
        session.markFailed("INTERNAL_SERVER_ERROR");

        assertThat(session.getScanStatus()).isEqualTo(ScanStatus.FAILED);
        assertThat(session.getFailureCode()).isEqualTo("SCAN_PROCESSING_TIMEOUT");
    }
}
