package com.hanspoon.backend_api.domain.ai.dto.analyze;

import java.util.UUID;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Prepare 요청 (백엔드 → AI). 가게 정보 없음. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PrepareRequest(String schemaVersion, UUID traceId, String scanSessionId, Image image, String locale) {

    public static final String SCHEMA_VERSION = "3.0";

    public static PrepareRequest of(UUID traceId, String scanSessionId, Image image, String locale) {
        return new PrepareRequest(SCHEMA_VERSION, traceId, scanSessionId, image, locale);
    }

    /** 검증된 S3 객체. source 는 camera | upload. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Image(String source, String storageKey, String versionId, String expectedEtag) {}
}
