package com.hanspoon.backend_api.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hanspoon.backend_api.domain.user.entity.AllergyCode;
import com.hanspoon.backend_api.domain.user.entity.ReligionType;
import com.hanspoon.backend_api.domain.user.entity.VegetarianType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * 온보딩(POST) / 프로필 수정(PATCH) 공용 요청. 프론트 UserProfile 1:1.
 * 교차검증(채식↔veganType, 종교↔religionType, 알레르기↔allergies)은 서비스에서 수행.
 */
public record ProfileRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}", message = "nationality must be ISO 3166-1 alpha-2.") String nationality,
        @NotBlank @Pattern(regexp = "ko|en|ar", message = "languageCode must be one of ko, en, ar.") String languageCode,
        @JsonProperty("isFirstTime") @NotNull Boolean isFirstTime,
        @JsonProperty("isVegan") @NotNull Boolean isVegan,
        VegetarianType veganType,
        @JsonProperty("hasReligion") @NotNull Boolean hasReligion,
        ReligionType religionType,
        @JsonProperty("hasAllergies") @NotNull Boolean hasAllergies,
        List<AllergyCode> allergies,
        @JsonProperty("noSpicy") @NotNull Boolean noSpicy,
        @JsonProperty("noAlcohol") @NotNull Boolean noAlcohol) {}
