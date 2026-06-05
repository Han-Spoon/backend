package com.hanspoon.backend_api.domain.ai.dto.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** GPT 에스컬레이션 사유. 룰엔진 코드 기준 3종 확정. AI 와이어값은 소문자 {@link #code}. */
public enum EscalationCase {
    UNKNOWN_REMAIN("unknown_remain"),
    UNKNOWN_MENU("unknown_menu"),
    AMBIGUITY("ambiguity");

    private final String code;

    EscalationCase(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static EscalationCase fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown escalation case: " + code));
    }
}
