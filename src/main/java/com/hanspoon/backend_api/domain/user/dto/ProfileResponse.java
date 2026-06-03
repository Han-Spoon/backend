package com.hanspoon.backend_api.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hanspoon.backend_api.domain.user.entity.AllergyCode;
import com.hanspoon.backend_api.domain.user.entity.ReligionType;
import com.hanspoon.backend_api.domain.user.entity.UserProfile;
import com.hanspoon.backend_api.domain.user.entity.VegetarianType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 프로필 조회/저장 응답. languageCode 는 GET /users/me 에서 제공(users 단일 출처). */
@Schema(description = "프로필 응답")
public record ProfileResponse(
        @Schema(description = "국적(ISO 3166-1 alpha-2)", example = "SA") String nationality,
        @Schema(description = "한식이 처음인지 여부", example = "true") @JsonProperty("isFirstTime") Boolean isFirstTime,
        @Schema(description = "채식 여부", example = "false") @JsonProperty("isVegan") Boolean isVegan,
        @Schema(description = "채식 유형", example = "lacto_ovo") VegetarianType veganType,
        @Schema(description = "종교적 식이 여부", example = "true") @JsonProperty("hasReligion") boolean hasReligion,
        @Schema(description = "종교 유형", example = "halal") ReligionType religionType,
        @Schema(description = "알레르기 보유 여부", example = "true") @JsonProperty("hasAllergies") boolean hasAllergies,
        @Schema(description = "알레르기 코드 목록") List<AllergyCode> allergies,
        @Schema(description = "매운 음식 불가", example = "true") @JsonProperty("noSpicy") Boolean noSpicy,
        @Schema(description = "알코올 불가", example = "true") @JsonProperty("noAlcohol") Boolean noAlcohol) {

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
