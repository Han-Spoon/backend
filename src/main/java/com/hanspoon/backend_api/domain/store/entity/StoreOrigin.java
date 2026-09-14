package com.hanspoon.backend_api.domain.store.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum StoreOrigin {
    SBIZ("sbiz"),
    LOCAL_DATA("localdata");

    private final String code;

    StoreOrigin(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static StoreOrigin fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown store origin: " + code));
    }
}
