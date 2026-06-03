package com.hanspoon.backend_api.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hanspoon.backend_api.domain.user.entity.AllergyCode;
import com.hanspoon.backend_api.domain.user.entity.ReligionType;
import com.hanspoon.backend_api.domain.user.entity.VegetarianType;
import com.hanspoon.backend_api.global.validation.CountryCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * 온보딩(POST) / 프로필 수정(PATCH) 공용 요청. 프론트 UserProfile 1:1.
 */
@Schema(description = "온보딩/프로필 수정 요청")
public record ProfileRequest(
        @Schema(description = "국적(ISO 3166-1 alpha-2)", example = "SA") @NotBlank @CountryCode String nationality,
        @Schema(description = "표시 언어(ko/en/ar)", example = "en")
                @NotBlank @Pattern(regexp = "ko|en|ar", message = "languageCode must be one of ko, en, ar.") String languageCode,
        @Schema(description = "한식이 처음인지 여부", example = "true") @JsonProperty("isFirstTime") @NotNull Boolean isFirstTime,
        @Schema(description = "채식 여부", example = "false") @JsonProperty("isVegan") @NotNull Boolean isVegan,
        @Schema(description = "채식 유형(isVegan=true일 때 필수)", example = "lacto_ovo") VegetarianType veganType,
        @Schema(description = "종교적 식이 여부", example = "true") @JsonProperty("hasReligion") @NotNull Boolean hasReligion,
        @Schema(description = "종교 유형(hasReligion=true일 때 필수)", example = "halal") ReligionType religionType,
        @Schema(description = "알레르기 보유 여부", example = "true") @JsonProperty("hasAllergies") @NotNull Boolean hasAllergies,
        @Schema(description = "알레르기 코드 목록(식약처 19종, hasAllergies=true일 때 필수)") List<AllergyCode> allergies,
        @Schema(description = "매운 음식 불가", example = "true") @JsonProperty("noSpicy") @NotNull Boolean noSpicy,
        @Schema(description = "알코올 불가", example = "true") @JsonProperty("noAlcohol") @NotNull Boolean noAlcohol) {}
