package com.hanspoon.backend_api.domain.scan.entity;

import com.hanspoon.backend_api.domain.ai.dto.common.RiskLevel;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * menu_analyses.risk_level 저장용. AI 계약과 동일 어휘를 쓰기 위해 {@link RiskLevel}(danger/caution/safe)을 재사용.
 * autoApply=true 이지만 RiskLevel 은 JPA 엔티티에서 MenuAnalysis.riskLevel 에만 쓰여 영향 범위가 한정된다.
 */
@Converter(autoApply = true)
public class RiskLevelConverter implements AttributeConverter<RiskLevel, String> {

    @Override
    public String convertToDatabaseColumn(RiskLevel attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public RiskLevel convertToEntityAttribute(String dbData) {
        return dbData == null ? null : RiskLevel.fromCode(dbData);
    }
}
