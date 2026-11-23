package com.hanspoon.backend_api.domain.scan.service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 스캔 diet_snapshot → 금지 태그. AI ai_decision_policy/profile.py forbidden_tags 이식.
 * 표를 바꾸면 AI 쪽과 함께 바꿀 것.
 */
final class DietForbiddenTags {

    private static final JsonMapper JSON = JsonMapper.shared();

    private static final Set<String> MEAT = Set.of("is_pork", "is_beef", "is_chicken", "is_duck");
    private static final Set<String> SEAFOOD =
            Set.of("is_fish", "is_shrimp", "is_crab", "is_squid", "is_mackerel", "is_shellfish");

    private static final Map<String, Set<String>> RELIGION = Map.of(
            "halal", Set.of("is_pork", "is_alcohol"),
            "kosher", Set.of("is_pork", "is_crab", "is_shrimp", "is_shellfish", "is_squid"),
            "hindu", Set.of("is_beef"));

    private static final Map<String, Set<String>> VEGETARIAN = Map.of(
            "vegan", union(MEAT, SEAFOOD, Set.of("is_milk", "is_egg")),
            "lacto", union(MEAT, SEAFOOD, Set.of("is_egg")),
            "ovo", union(MEAT, SEAFOOD, Set.of("is_milk")),
            "lacto_ovo", union(MEAT, SEAFOOD),
            "pesco", MEAT);

    private DietForbiddenTags() {}

    /** 스냅샷 없으면 빈 집합. */
    static Set<String> of(String dietSnapshot) {
        Set<String> tags = new HashSet<>();
        if (dietSnapshot == null) {
            return tags;
        }
        JsonNode node = JSON.readTree(dietSnapshot);
        tags.addAll(RELIGION.getOrDefault(text(node, "religion_type"), Set.of()));
        tags.addAll(VEGETARIAN.getOrDefault(text(node, "vegetarian_type"), Set.of()));
        if (node.path("no_alcohol").asBoolean(false)) {
            tags.add("is_alcohol");
        }
        if (node.path("no_spicy").asBoolean(false)) {
            tags.add("is_spicy");
        }
        node.path("allergies").forEach(tag -> tags.add(tag.asString()));
        return tags;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asString();
    }

    @SafeVarargs
    private static Set<String> union(Set<String>... sets) {
        Set<String> all = new HashSet<>();
        for (Set<String> set : sets) {
            all.addAll(set);
        }
        return Set.copyOf(all);
    }
}
