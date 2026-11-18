package com.hanspoon.backend_api.domain.scan.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import com.hanspoon.backend_api.domain.store.entity.StoreMatchMethod;
import com.hanspoon.backend_api.domain.store.entity.StoreMatchMethodConverter;
import org.junit.jupiter.api.Test;

class ScanConverterTest {

    private final ScanStatusConverter scanStatusConverter = new ScanStatusConverter();
    private final RiskLevelConverter riskLevelConverter = new RiskLevelConverter();
    private final StoreMatchMethodConverter storeMatchMethodConverter = new StoreMatchMethodConverter();

    @Test
    void scanStatusConvertsBothWays() {
        assertThat(scanStatusConverter.convertToDatabaseColumn(ScanStatus.NEEDS_RETAKE))
                .isEqualTo("needs_retake");
        assertThat(scanStatusConverter.convertToEntityAttribute("completed")).isEqualTo(ScanStatus.COMPLETED);
    }

    @Test
    void scanStatusHandlesNull() {
        assertThat(scanStatusConverter.convertToDatabaseColumn(null)).isNull();
        assertThat(scanStatusConverter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void riskLevelConvertsBothWays() {
        assertThat(riskLevelConverter.convertToDatabaseColumn(RiskLevel.DANGER)).isEqualTo("danger");
        assertThat(riskLevelConverter.convertToEntityAttribute("safe")).isEqualTo(RiskLevel.SAFE);
    }

    @Test
    void storeMatchMethodConvertsStoreDetail() {
        assertThat(storeMatchMethodConverter.convertToDatabaseColumn(StoreMatchMethod.STORE_DETAIL))
                .isEqualTo("store_detail");
        assertThat(storeMatchMethodConverter.convertToEntityAttribute("store_detail"))
                .isEqualTo(StoreMatchMethod.STORE_DETAIL);
    }

    @Test
    void riskLevelHandlesNull() {
        assertThat(riskLevelConverter.convertToDatabaseColumn(null)).isNull();
        assertThat(riskLevelConverter.convertToEntityAttribute(null)).isNull();
    }
}
