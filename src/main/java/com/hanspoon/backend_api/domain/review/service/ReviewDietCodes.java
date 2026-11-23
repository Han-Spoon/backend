package com.hanspoon.backend_api.domain.review.service;

import com.hanspoon.backend_api.domain.user.entity.AllergyCode;
import com.hanspoon.backend_api.domain.user.entity.ReligionType;
import com.hanspoon.backend_api.domain.user.entity.UserProfile;
import java.util.LinkedHashSet;
import java.util.Set;

/** 프로필 → 후기 식단 코드(meal_review_answers.diet_code). 채식은 DietForbiddenTags 와 같이 vegetarian_type 기준. */
final class ReviewDietCodes {

    private ReviewDietCodes() {}

    static Set<String> of(UserProfile profile) {
        Set<String> codes = new LinkedHashSet<>();
        if (profile.getVegetarianType() != null) {
            codes.add("vegan:" + profile.getVegetarianType().getCode());
        }
        if (profile.getReligionType() != null && profile.getReligionType() != ReligionType.NONE) {
            codes.add("religion:" + profile.getReligionType().getCode());
        }
        for (AllergyCode allergy : profile.getAllergies()) {
            codes.add("allergy:" + allergy.getCode());
        }
        if (Boolean.TRUE.equals(profile.getNoSpicy())) {
            codes.add("preference:no-spicy");
        }
        if (Boolean.TRUE.equals(profile.getNoAlcohol())) {
            codes.add("preference:no-alcohol");
        }
        return codes;
    }
}
