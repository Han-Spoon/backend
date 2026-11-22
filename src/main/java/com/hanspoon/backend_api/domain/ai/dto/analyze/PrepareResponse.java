package com.hanspoon.backend_api.domain.ai.dto.analyze;

import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * Prepare 응답 (AI → 백엔드).
 *
 * <p>preparedItems·prepareSignals 는 해시 대상이라 원본 JSON 그대로 보관·전달.
 * 읽기용 값은 {@link PreparedItem} 으로 변환.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PrepareResponse(
        String schemaVersion,
        UUID traceId,
        UUID prepareId,
        String scanSessionId,
        String preparedItemsHash,
        ScanQuality scanQuality,
        JsonNode prepareSignals,
        JsonNode preparedItems,
        List<NodeWarning> warnings,
        List<NodeError> errors) {

    public static final String USABLE = "usable";

    public boolean usable() {
        return scanQuality != null && USABLE.equals(scanQuality.status());
    }

    /** status: usable | low_confidence | needs_retake. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ScanQuality(String status, List<String> reasons, List<String> retakeSuggestions) {}
}
