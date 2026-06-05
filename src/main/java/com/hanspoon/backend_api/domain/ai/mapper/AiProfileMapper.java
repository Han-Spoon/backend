package com.hanspoon.backend_api.domain.ai.mapper;

import com.hanspoon.backend_api.domain.ai.dto.ruleengine.RuleProfile;
import com.hanspoon.backend_api.domain.user.entity.AllergyCode;
import com.hanspoon.backend_api.domain.user.entity.ReligionType;
import com.hanspoon.backend_api.domain.user.entity.UserAllergy;
import com.hanspoon.backend_api.domain.user.entity.UserProfile;
import com.hanspoon.backend_api.domain.user.entity.VegetarianType;
import java.util.List;

/**
 * {@link UserProfile} + {@link UserAllergy} 목록을 룰엔진 입력 {@link RuleProfile} 로 변환하는 순수 함수.
 */
public final class AiProfileMapper {

    private AiProfileMapper() {}

    /** 알레르기 코드 → 룰엔진 {@code is_*} 태그. */
    public static String toAllergyTag(AllergyCode code) {
        return "is_" + code.getCode();
    }

    public static RuleProfile toRuleProfile(UserProfile profile, List<UserAllergy> allergies) {
        return new RuleProfile(
                mapReligion(profile.getReligionType()),
                profile.getVegetarian(),
                mapVegetarianType(profile.getVegetarian(), profile.getVegetarianType()),
                profile.getNoAlcohol(),
                mapAllergies(allergies),
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

    private static List<String> mapAllergies(List<UserAllergy> allergies) {
        if (allergies == null) {
            return List.of();
        }
        return allergies.stream()
                .map(UserAllergy::getAllergyCode)
                .map(AiProfileMapper::toAllergyTag)
                .toList();
    }
}
