package com.hanspoon.backend_api.domain.store.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** {@link StoreMatchMethod}와 DB 소문자 코드 간 변환기. */
@Converter(autoApply = true)
public class StoreMatchMethodConverter implements AttributeConverter<StoreMatchMethod, String> {

    @Override
    public String convertToDatabaseColumn(StoreMatchMethod attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public StoreMatchMethod convertToEntityAttribute(String dbData) {
        return dbData == null ? null : StoreMatchMethod.fromCode(dbData);
    }
}
