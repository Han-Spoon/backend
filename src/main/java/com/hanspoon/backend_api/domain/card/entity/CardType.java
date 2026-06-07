package com.hanspoon.backend_api.domain.card.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** 자주 쓰는 카드 종류. 와이어/DB 저장값은 소문자 {@link #code}. */
public enum CardType {
    ORDER("order"),
    INGREDIENT_CHECK("ingredient_check"),
    EXCLUDE("exclude"),
    OWNER_QUESTION("owner_question");

    private final String code;

    CardType(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static CardType fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown card type: " + code));
    }
}
