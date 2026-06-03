package com.hanspoon.backend_api.domain.user.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/**
 * 식약처 알레르기 유발물질 표시대상 19종 + 알코올.
 * JSON 와이어값/DB 저장값 모두 소문자 저장. {@link #code}
 */
public enum AllergyCode {
    EGG("egg"),
    MILK("milk"),
    BUCKWHEAT("buckwheat"),
    PEANUT("peanut"),
    SOYBEAN("soybean"),
    WHEAT("wheat"),
    MACKEREL("mackerel"),
    CRAB("crab"),
    SHRIMP("shrimp"),
    PORK("pork"),
    PEACH("peach"),
    TOMATO("tomato"),
    SULFITE("sulfite"),
    WALNUT("walnut"),
    CHICKEN("chicken"),
    BEEF("beef"),
    SQUID("squid"),
    SHELLFISH("shellfish"),
    PINE_NUT("pine_nut"),
    ALCOHOL("alcohol");

    private final String code;

    AllergyCode(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static AllergyCode fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown allergy code: " + code));
    }
}
