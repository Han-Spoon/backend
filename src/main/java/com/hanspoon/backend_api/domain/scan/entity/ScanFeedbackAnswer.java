package com.hanspoon.backend_api.domain.scan.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** 가게에서 식단 요구사항을 전달한 경험. 응답하지 않은 항목은 JSON 객체에서 생략. */
public enum ScanFeedbackAnswer {
    YES("yes"),
    NO("no"),
    UNKNOWN("unknown");

    private final String code;

    ScanFeedbackAnswer(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static ScanFeedbackAnswer fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown scan feedback answer: " + code));
    }
}
