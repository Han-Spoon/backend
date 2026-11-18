package com.hanspoon.backend_api.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hanspoon.backend_api.domain.user.entity.AllergyCode;
import com.hanspoon.backend_api.domain.user.entity.ReligionType;
import com.hanspoon.backend_api.domain.user.entity.VegetarianType;
import com.hanspoon.backend_api.global.validation.CountryCode;
import com.hanspoon.backend_api.global.validation.SupportedLanguages;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * 프로필 부분 수정(PATCH) 요청. 모든 필드 선택, null 은 변경 없음.
 *
 * <p>상세값 삭제는 상위 플래그 false 로. 조건 규칙은 저장값과 합친 결과로 검사.
 */
@Schema(description = "프로필 부분 수정 요청(보낸 필드만 변경)")
public record ProfilePatchRequest(
        @Schema(description = "국적(ISO 3166-1 alpha-2)", example = "SA")
                @Pattern(regexp = "[A-Za-z]{2}", message = "nationality must be an ISO 3166-1 alpha-2 code.") @CountryCode
                String nationality,
        @Schema(description = "표시 언어(7종: ko, en, ar, zh-CN, zh-TW, ja, es)", example = "ja")
                @Pattern(regexp = SupportedLanguages.PATTERN, message = SupportedLanguages.MESSAGE) String languageCode,
        @Schema(description = "한식이 처음인지 여부", example = "true") @JsonProperty("isFirstTime") Boolean isFirstTime,
        @Schema(description = "채식 여부", example = "true") @JsonProperty("isVegan") Boolean isVegan,
        @Schema(description = "채식 유형(합친 결과 isVegan=true면 필수)", example = "lacto_ovo") VegetarianType veganType,
        @Schema(description = "종교적 식이 여부", example = "true") @JsonProperty("hasReligion") Boolean hasReligion,
        @Schema(description = "종교 유형(합친 결과 hasReligion=true면 필수, none 불가)", example = "halal")
                ReligionType religionType,
        @Schema(description = "알레르기 보유 여부", example = "true") @JsonProperty("hasAllergies") Boolean hasAllergies,
        @Schema(description = "알레르기 코드 목록(식약처 19종, 합친 결과 hasAllergies=true면 1개 이상)")
                List<@NotNull AllergyCode> allergies,
        @Schema(description = "매운 음식 불가", example = "true") @JsonProperty("noSpicy") Boolean noSpicy,
        @Schema(description = "알코올 불가", example = "true") @JsonProperty("noAlcohol") Boolean noAlcohol) {}
