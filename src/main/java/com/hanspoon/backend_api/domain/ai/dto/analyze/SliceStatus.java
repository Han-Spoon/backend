package com.hanspoon.backend_api.domain.ai.dto.analyze;

import com.fasterxml.jackson.annotation.JsonValue;

/** KnowledgeBundle 조회 영역 상태. */
public enum SliceStatus {
    OK("ok"),
    NOT_FOUND("not_found"),
    UNAVAILABLE("unavailable");

    private final String code;

    SliceStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }
}
