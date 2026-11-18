package com.hanspoon.backend_api.global.exception;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;

/** 에러 응답(ProblemDetail) 공통 필드. */
public final class ProblemDetails {

    private static final String TYPE_BASE = "https://api.han-spoon.site/problems/";

    private ProblemDetails() {}

    public static URI type(String code) {
        return URI.create(TYPE_BASE + code);
    }

    /** code(이미 있으면 유지), timestamp(UTC), traceId, instance 채움. 반환값은 traceId. */
    public static String enrich(ProblemDetail problemDetail, String code, String path) {
        Map<String, Object> properties = problemDetail.getProperties();
        if (properties == null || properties.get("code") == null) {
            problemDetail.setProperty("code", code);
        }
        String traceId = traceId();
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("traceId", traceId);
        problemDetail.setInstance(URI.create(path));
        return traceId;
    }

    private static String traceId() {
        String traceId = MDC.get("traceId");
        return traceId != null && !traceId.isBlank()
                ? traceId
                : UUID.randomUUID().toString().replace("-", "");
    }
}
