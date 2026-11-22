package com.hanspoon.backend_api.domain.ai.mapper;

import com.hanspoon.backend_api.domain.ai.dto.analyze.DietProfile;
import com.hanspoon.backend_api.domain.user.entity.AllergyCode;
import com.hanspoon.backend_api.domain.user.entity.ReligionType;
import com.hanspoon.backend_api.domain.user.entity.UserProfile;
import com.hanspoon.backend_api.domain.user.entity.VegetarianType;
import java.util.List;

/**
 * {@link UserProfile}(알레르기 배열 포함) → Judge 입력 {@link DietProfile}.
 */
public final class AiProfileMapper {

    private AiProfileMapper() {}

    /** 알레르기 코드 → {@code is_*} 태그. */
    public static String toAllergyTag(AllergyCode code) {
        return "is_" + code.getCode();
    }

    public static DietProfile toDietProfile(UserProfile profile) {
        return new DietProfile(
                mapReligion(profile.getReligionType()),
                profile.getVegetarian(),
                mapVegetarianType(profile.getVegetarian(), profile.getVegetarianType()),
                profile.getNoAlcohol(),
                mapAllergies(profile.getAllergies()),
                profile.getNoSpicy());
    }

    private static String mapReligion(ReligionType religionType) {
        if (religionType == null || religionType == ReligionType.NONE) {
            return null;
        }
        return religionType.getCode();
    }

    private static String mapVegetarianType(Boolean vegetarian, VegetarianType vegetarianType) {
        if (!Boolean.TRUE.equals(vegetarian) || vegetarianType == null) {
            return null;
        }
        return vegetarianType.getCode();
    }

    private static List<String> mapAllergies(List<AllergyCode> allergies) {
        return allergies.stream().map(AiProfileMapper::toAllergyTag).toList();
    }
}
