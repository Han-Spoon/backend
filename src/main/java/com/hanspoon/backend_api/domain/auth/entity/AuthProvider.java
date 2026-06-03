package com.hanspoon.backend_api.domain.auth.entity;

import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthProvider {
    GOOGLE("google");

    private final String code;

    public static AuthProvider fromCode(String code) {
        return Arrays.stream(values())
                .filter(provider -> provider.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown auth provider code: " + code));
    }
}
