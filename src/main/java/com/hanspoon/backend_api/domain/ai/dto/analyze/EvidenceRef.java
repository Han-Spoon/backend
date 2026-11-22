package com.hanspoon.backend_api.domain.ai.dto.analyze;

import java.time.Instant;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** 근거 참조. observedAt 은 UTC. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EvidenceRef(
        String evidenceId,
        String evidenceClass,
        String sourceType,
        String sourceRef,
        String verificationStatus,
        Double reliabilityWeight,
        Instant observedAt) {}
