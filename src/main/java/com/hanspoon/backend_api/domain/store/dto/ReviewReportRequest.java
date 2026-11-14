package com.hanspoon.backend_api.domain.store.dto;

import jakarta.validation.constraints.NotNull;

public record ReviewReportRequest(@NotNull Reason reason) {
    public enum Reason {
        INAPPROPRIATE,
        FALSE_INFORMATION,
        AD_SPAM,
        OTHER
    }
}
