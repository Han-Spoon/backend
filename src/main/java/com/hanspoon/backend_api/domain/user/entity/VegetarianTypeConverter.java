package com.hanspoon.backend_api.domain.user.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** {@link VegetarianType} ↔ DB 컬럼(소문자 code) 변환기. */
@Converter(autoApply = true)
public class VegetarianTypeConverter implements AttributeConverter<VegetarianType, String> {

    @Override
    public String convertToDatabaseColumn(VegetarianType attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public VegetarianType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : VegetarianType.fromCode(dbData);
    }
}
