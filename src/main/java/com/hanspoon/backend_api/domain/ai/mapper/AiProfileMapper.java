package com.hanspoon.backend_api.domain.ai.mapper;

import com.hanspoon.backend_api.domain.ai.dto.ruleengine.RuleProfile;
import com.hanspoon.backend_api.domain.user.entity.AllergyCode;
import com.hanspoon.backend_api.domain.user.entity.ReligionType;
import com.hanspoon.backend_api.domain.user.entity.UserAllergy;
import com.hanspoon.backend_api.domain.user.entity.UserProfile;
import com.hanspoon.backend_api.domain.user.entity.VegetarianType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * {@link UserProfile} + {@link UserAllergy} 목록을 룰엔진 입력 {@link RuleProfile} 로 변환하는 순수 함수.
 *
 * <p>알레르기 코드는 백엔드의 식약처 19종 코드(예: {@code milk})를 룰엔진의 {@code is_*} 태그(예:
 * {@code is_milk})로 변환한다. 대부분 {@code "is_" + code} 이지만 {@code pine_nut → is_pinenut} 처럼
 * 다른 케이스가 있어 암묵 변환 대신 명시적 매핑을 둔다.
 */
public final class AiProfileMapper {

    private static final Map<AllergyCode, String> ALLERGY_TAGS = new EnumMap<>(AllergyCode.class);

    static {
        ALLERGY_TAGS.put(AllergyCode.EGG, "is_egg");
        ALLERGY_TAGS.put(AllergyCode.MILK, "is_milk");
        ALLERGY_TAGS.put(AllergyCode.BUCKWHEAT, "is_buckwheat");
        ALLERGY_TAGS.put(AllergyCode.PEANUT, "is_peanut");
        ALLERGY_TAGS.put(AllergyCode.SOYBEAN, "is_soybean");
        ALLERGY_TAGS.put(AllergyCode.WHEAT, "is_wheat");
        ALLERGY_TAGS.put(AllergyCode.MACKEREL, "is_mackerel");
        ALLERGY_TAGS.put(AllergyCode.CRAB, "is_crab");
        ALLERGY_TAGS.put(AllergyCode.SHRIMP, "is_shrimp");
        ALLERGY_TAGS.put(AllergyCode.PORK, "is_pork");
        ALLERGY_TAGS.put(AllergyCode.PEACH, "is_peach");
        ALLERGY_TAGS.put(AllergyCode.TOMATO, "is_tomato");
        ALLERGY_TAGS.put(AllergyCode.SULFITE, "is_sulfite");
        ALLERGY_TAGS.put(AllergyCode.WALNUT, "is_walnut");
        ALLERGY_TAGS.put(AllergyCode.CHICKEN, "is_chicken");
        ALLERGY_TAGS.put(AllergyCode.BEEF, "is_beef");
        ALLERGY_TAGS.put(AllergyCode.SQUID, "is_squid");
        ALLERGY_TAGS.put(AllergyCode.SHELLFISH, "is_shellfish");
        ALLERGY_TAGS.put(AllergyCode.PINE_NUT, "is_pinenut");
    }

    private AiProfileMapper() {}

    /** 알레르기 코드 → 룰엔진 {@code is_*} 태그. */
    public static String toAllergyTag(AllergyCode code) {
        String tag = ALLERGY_TAGS.get(code);
        if (tag == null) {
            throw new IllegalArgumentException("Unmapped allergy code: " + code);
        }
        return tag;
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
