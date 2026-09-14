package com.hanspoon.backend_api.domain.store.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** {@link StoreOrigin}과 DB 소문자 코드 간 변환기. */
@Converter(autoApply = true)
public class StoreOriginConverter implements AttributeConverter<StoreOrigin, String> {

    @Override
    public String convertToDatabaseColumn(StoreOrigin attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public StoreOrigin convertToEntityAttribute(String dbData) {
        return dbData == null ? null : StoreOrigin.fromCode(dbData);
    }
}
