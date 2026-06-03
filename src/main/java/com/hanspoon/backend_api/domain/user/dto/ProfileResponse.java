package com.hanspoon.backend_api.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hanspoon.backend_api.domain.user.entity.AllergyCode;
import com.hanspoon.backend_api.domain.user.entity.ReligionType;
import com.hanspoon.backend_api.domain.user.entity.UserProfile;
import com.hanspoon.backend_api.domain.user.entity.VegetarianType;
import java.util.List;

/** 프로필 조회/저장 응답. languageCode 는 GET /users/me 에서 제공(users 단일 출처). */
public record ProfileResponse(
        String nationality,
        @JsonProperty("isFirstTime") Boolean isFirstTime,
        @JsonProperty("isVegan") Boolean isVegan,
        VegetarianType veganType,
        @JsonProperty("hasReligion") boolean hasReligion,
        ReligionType religionType,
        @JsonProperty("hasAllergies") boolean hasAllergies,
        List<AllergyCode> allergies,
        @JsonProperty("noSpicy") Boolean noSpicy,
        @JsonProperty("noAlcohol") Boolean noAlcohol) {

    public static ProfileResponse of(UserProfile profile, List<AllergyCode> allergies) {
        List<AllergyCode> codes = allergies == null ? List.of() : allergies;
        boolean hasReligion = profile.getReligionType() != null && profile.getReligionType() != ReligionType.NONE;
        return new ProfileResponse(
                profile.getNationality(),
                profile.getFirstTime(),
                profile.getVegetarian(),
                profile.getVegetarianType(),
                hasReligion,
                profile.getReligionType(),
                !codes.isEmpty(),
                codes,
                profile.getNoSpicy(),
                profile.getNoAlcohol());
    }
}
