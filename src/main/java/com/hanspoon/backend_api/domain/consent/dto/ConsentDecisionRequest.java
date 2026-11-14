package com.hanspoon.backend_api.domain.consent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record ConsentDecisionRequest(@NotBlank String version, @NotNull Map<String, Boolean> decisions) {}
