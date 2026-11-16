package com.hanspoon.backend_api.domain.ai.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanspoon.backend_api.domain.ai.dto.ruleengine.RuleProfile;
import com.hanspoon.backend_api.domain.user.entity.AllergyCode;
import com.hanspoon.backend_api.domain.user.entity.ReligionType;
import com.hanspoon.backend_api.domain.user.entity.UserProfile;
import com.hanspoon.backend_api.domain.user.entity.VegetarianType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AiProfileMapperTest {

    private static UserProfile profile(Boolean vegetarian, VegetarianType vegetarianType, ReligionType religionType) {
        return profile(vegetarian, vegetarianType, religionType, List.of());
    }

    private static UserProfile profile(
            Boolean vegetarian, VegetarianType vegetarianType, ReligionType religionType, List<AllergyCode> allergies) {
        return UserProfile.create(
                UUID.randomUUID(), "KR", false, vegetarian, vegetarianType, religionType, true, true, allergies);
    }

    @Test
    void mapsReligionNoneToNull() {
        RuleProfile result = AiProfileMapper.toRuleProfile(profile(false, null, ReligionType.NONE));

        assertThat(result.religionType()).isNull();
    }

    @Test
    void mapsReligionCode() {
        RuleProfile result = AiProfileMapper.toRuleProfile(profile(false, null, ReligionType.HALAL));

        assertThat(result.religionType()).isEqualTo("halal");
    }

    @Test
    void dropsVegetarianTypeWhenNotVegetarian() {
        RuleProfile result = AiProfileMapper.toRuleProfile(profile(false, VegetarianType.VEGAN, ReligionType.NONE));

        assertThat(result.isVegetarian()).isFalse();
        assertThat(result.vegetarianType()).isNull();
    }

    @Test
    void keepsVegetarianTypeWhenVegetarian() {
        RuleProfile result = AiProfileMapper.toRuleProfile(profile(true, VegetarianType.LACTO_OVO, ReligionType.NONE));

        assertThat(result.isVegetarian()).isTrue();
        assertThat(result.vegetarianType()).isEqualTo("lacto_ovo");
    }

    @Test
    void carriesNoAlcoholAndNoSpicy() {
        RuleProfile result = AiProfileMapper.toRuleProfile(profile(false, null, ReligionType.NONE));

        assertThat(result.noAlcohol()).isTrue();
        assertThat(result.noSpicy()).isTrue();
    }

    @Test
    void mapsPineNutToIsPinenut() {
        assertThat(AiProfileMapper.toAllergyTag(AllergyCode.PINE_NUT)).isEqualTo("is_pinenut");
    }

    @Test
    void mapsEveryAllergyCodeToIsPrefixedTag() {
        for (AllergyCode code : AllergyCode.values()) {
            String tag = AiProfileMapper.toAllergyTag(code);
            assertThat(tag).startsWith("is_");
        }
    }

    @Test
    void mapsAllergyListSortedByCode() {
        RuleProfile result = AiProfileMapper.toRuleProfile(
                profile(false, null, ReligionType.NONE, List.of(AllergyCode.SOYBEAN, AllergyCode.MILK)));

        assertThat(result.allergies()).containsExactly("is_milk", "is_soybean");
    }

    @Test
    void handlesEmptyAllergies() {
        RuleProfile result = AiProfileMapper.toRuleProfile(profile(false, null, ReligionType.NONE));

        assertThat(result.allergies()).isEmpty();
    }
}
