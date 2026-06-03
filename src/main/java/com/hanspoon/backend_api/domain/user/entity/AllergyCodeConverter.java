package com.hanspoon.backend_api.domain.user.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** {@link AllergyCode} ↔ DB 컬럼(소문자 code) 변환기. */
@Converter(autoApply = true)
public class AllergyCodeConverter implements AttributeConverter<AllergyCode, String> {

    @Override
    public String convertToDatabaseColumn(AllergyCode attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public AllergyCode convertToEntityAttribute(String dbData) {
        return dbData == null ? null : AllergyCode.fromCode(dbData);
    }
}
