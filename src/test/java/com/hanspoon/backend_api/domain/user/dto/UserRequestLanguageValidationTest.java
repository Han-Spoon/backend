package com.hanspoon.backend_api.domain.user.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 언어 코드 검증: 지원 언어 7종(DB CHECK ck_users_language_code 와 같음)만 받는다. */
class UserRequestLanguageValidationTest {

    private static jakarta.validation.ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ko", "en", "ar", "zh-CN", "zh-TW", "ja", "es"})
    void acceptsSupportedLanguages(String languageCode) {
        assertThat(validator.validate(new UpdateUserRequest(languageCode, null)))
                .isEmpty();
        assertThat(validator.validate(profileRequest(languageCode))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"fr", "zh", "zh-cn", "EN", ""})
    void rejectsUnsupportedLanguages(String languageCode) {
        assertThat(validator.validate(new UpdateUserRequest(languageCode, null)))
                .isNotEmpty();
        assertThat(validator.validate(profileRequest(languageCode))).isNotEmpty();
    }

    private static ProfileRequest profileRequest(String languageCode) {
        return new ProfileRequest("SA", languageCode, true, false, null, false, null, false, null, false, false);
    }
}
