package com.hanspoon.backend_api.domain.user.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** 채식 유형. JSON 와이어값/DB 저장값 모두 소문자 {@link #code}. */
public enum VegetarianType {
    VEGAN("vegan"),
    LACTO("lacto"),
    OVO("ovo"),
    LACTO_OVO("lacto_ovo"),
    PESCO("pesco");

    private final String code;

    VegetarianType(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static VegetarianType fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown vegetarian type: " + code));
    }
}
