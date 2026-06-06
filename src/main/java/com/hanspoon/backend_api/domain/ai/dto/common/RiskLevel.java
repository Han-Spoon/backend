package com.hanspoon.backend_api.domain.ai.dto.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** 메뉴 위험도 최종 판정. AI 와이어값은 소문자 {@link #code}. */
public enum RiskLevel {
    DANGER("danger"),
    CAUTION("caution"),
    SAFE("safe");

    private final String code;

    RiskLevel(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static RiskLevel fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown risk level: " + code));
    }
}
