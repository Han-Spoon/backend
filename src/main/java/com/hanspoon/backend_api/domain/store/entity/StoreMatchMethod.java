package com.hanspoon.backend_api.domain.store.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** 사용자가 가게 후보를 찾은 경로. 판정 근거가 아니라 검색 품질 관측용 메타데이터다. */
public enum StoreMatchMethod {
    GPS_CANDIDATE("gps_candidate"),
    NAME_SEARCH("name_search"),
    KAKAO_FALLBACK("kakao_fallback");

    private final String code;

    StoreMatchMethod(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static StoreMatchMethod fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown store match method: " + code));
    }
}
