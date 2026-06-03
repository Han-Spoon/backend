package com.hanspoon.backend_api.domain.user.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** 종교적 식이. JSON 와이어값/DB 저장값 모두 소문자 {@link #code}. */
public enum ReligionType {
    HALAL("halal"),
    KOSHER("kosher"),
    HINDU("hindu"),
    NONE("none");

    private final String code;

    ReligionType(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static ReligionType fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown religion type: " + code));
    }
}
