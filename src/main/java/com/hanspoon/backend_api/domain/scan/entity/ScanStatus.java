package com.hanspoon.backend_api.domain.scan.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** 스캔 세션 상태. 백엔드 라이프사이클이 소유. DB 저장값은 소문자 */
public enum ScanStatus {
    PROCESSING("processing"),
    COMPLETED("completed"),
    FAILED("failed"),
    NEEDS_RETAKE("needs_retake");

    private final String code;

    ScanStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static ScanStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown scan status: " + code));
    }
}
