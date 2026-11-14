package com.hanspoon.backend_api.domain.consent.dto;

import java.util.Map;

public record ConsentStatusResponse(
        String version,
        Map<String, Boolean> decisions,
        boolean hasRequiredConsents,
        boolean guardianManaged,
        boolean guardianApprovalRequired) {}
