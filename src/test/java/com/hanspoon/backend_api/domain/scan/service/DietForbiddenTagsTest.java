package com.hanspoon.backend_api.domain.scan.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DietForbiddenTagsTest {

    @Test
    void combinesReligionVegetarianPreferencesAndAllergies() {
        String diet =
                """
                {"religion_type":"hindu","is_vegetarian":true,"vegetarian_type":"pesco","no_alcohol":true,
                 "allergies":["is_peanut"],"no_spicy":true}""";

        assertThat(DietForbiddenTags.of(diet))
                .containsExactlyInAnyOrder(
                        "is_beef", "is_pork", "is_chicken", "is_duck", "is_alcohol", "is_spicy", "is_peanut");
    }

    @Test
    void veganForbidsMeatSeafoodMilkAndEgg() {
        assertThat(DietForbiddenTags.of("{\"vegetarian_type\":\"vegan\",\"allergies\":[]}"))
                .hasSize(12)
                .contains("is_shellfish", "is_milk", "is_egg");
    }

    @Test
    void emptyWithoutSnapshotOrRestrictions() {
        assertThat(DietForbiddenTags.of(null)).isEmpty();
        assertThat(DietForbiddenTags.of("{\"religion_type\":null,\"vegetarian_type\":null,\"allergies\":[]}"))
                .isEmpty();
    }
}
