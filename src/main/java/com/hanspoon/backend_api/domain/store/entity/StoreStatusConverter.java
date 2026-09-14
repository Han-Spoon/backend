package com.hanspoon.backend_api.domain.store.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** {@link StoreStatus}와 DB 소문자 코드 간 변환기. */
@Converter(autoApply = true)
public class StoreStatusConverter implements AttributeConverter<StoreStatus, String> {

    @Override
    public String convertToDatabaseColumn(StoreStatus attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public StoreStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : StoreStatus.fromCode(dbData);
    }
}
