package com.hanspoon.backend_api.domain.auth.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@link AuthProvider} ↔ DB 컬럼(소문자 code) 변환기.
 * autoApply=true 이므로 AuthProvider 타입 필드에 자동 적용된다.
 */
@Converter(autoApply = true)
public class AuthProviderConverter implements AttributeConverter<AuthProvider, String> {

	@Override
	public String convertToDatabaseColumn(AuthProvider attribute) {
		return attribute == null ? null : attribute.getCode();
	}

	@Override
	public AuthProvider convertToEntityAttribute(String dbData) {
		return dbData == null ? null : AuthProvider.fromCode(dbData);
	}
}
