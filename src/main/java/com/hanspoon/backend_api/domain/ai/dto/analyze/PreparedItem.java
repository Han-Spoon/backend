package com.hanspoon.backend_api.domain.ai.dto.analyze;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Prepare 메뉴 항목의 읽기용 값. 전달에는 원본 JSON 사용. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PreparedItem(
        String itemId,
        int sourceIndex,
        String rawMenuName,
        String normalizedMenuName,
        String displayName,
        String description,
        String priceText,
        JsonNode priceOptions,
        List<RiskTagObservation> riskTagObservations) {

    private static final String SPICY = "is_spicy";

    public boolean spicyObserved() {
        return riskTagObservations != null
                && riskTagObservations.stream().anyMatch(observation -> SPICY.equals(observation.tag()));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RiskTagObservation(String tag) {}
}
