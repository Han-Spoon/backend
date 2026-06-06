package com.hanspoon.backend_api.domain.scan.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ScanStatusConverter implements AttributeConverter<ScanStatus, String> {

    @Override
    public String convertToDatabaseColumn(ScanStatus attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public ScanStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ScanStatus.fromCode(dbData);
    }
}
