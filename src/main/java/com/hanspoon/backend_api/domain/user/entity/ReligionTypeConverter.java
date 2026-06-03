package com.hanspoon.backend_api.domain.user.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** {@link ReligionType} ↔ DB 컬럼(소문자 code) 변환기. */
@Converter(autoApply = true)
public class ReligionTypeConverter implements AttributeConverter<ReligionType, String> {

    @Override
    public String convertToDatabaseColumn(ReligionType attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public ReligionType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : ReligionType.fromCode(dbData);
    }
}
