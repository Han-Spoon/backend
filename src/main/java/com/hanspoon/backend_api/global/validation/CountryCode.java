package com.hanspoon.backend_api.global.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ISO 3166-1 alpha-2 국가 코드 검증. 실제 유효 코드 집합은 {@link java.util.Locale#getISOCountries()} 기준.
 * 대소문자 무관(검증 시 대문자로 비교).
 */
@Documented
@Constraint(validatedBy = CountryCodeValidator.class)
@Target({
    ElementType.METHOD,
    ElementType.FIELD,
    ElementType.PARAMETER,
    ElementType.RECORD_COMPONENT,
    ElementType.TYPE_USE
})
@Retention(RetentionPolicy.RUNTIME)
public @interface CountryCode {

    String message() default "must be a valid ISO 3166-1 alpha-2 country code.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
