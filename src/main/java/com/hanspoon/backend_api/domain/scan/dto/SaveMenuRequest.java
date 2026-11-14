package com.hanspoon.backend_api.domain.scan.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SaveMenuRequest(@NotNull UUID scanId, @NotNull @Min(0) Integer displayOrder) {}
