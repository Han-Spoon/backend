package com.hanspoon.backend_api.global.validation;

import java.util.Set;

/** 앱이 지원하는 언어 코드 7종. DB CHECK(V8 ck_users_language_code 등)와 같은 목록이다. */
public final class SupportedLanguages {

    public static final Set<String> CODES = Set.of("ko", "en", "ar", "zh-CN", "zh-TW", "ja", "es");

    /** Bean Validation {@code @Pattern} 용. */
    public static final String PATTERN = "ko|en|ar|zh-CN|zh-TW|ja|es";

    public static final String MESSAGE = "languageCode must be one of ko, en, ar, zh-CN, zh-TW, ja, es.";

    private SupportedLanguages() {}
}
