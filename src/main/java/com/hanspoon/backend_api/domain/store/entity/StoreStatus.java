package com.hanspoon.backend_api.domain.store.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** 가게 노출 상태. */
public enum StoreStatus {
    ACTIVE("active"),
    INACTIVE("inactive");

    private final String code;

    StoreStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static StoreStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown store status: " + code));
    }
}
