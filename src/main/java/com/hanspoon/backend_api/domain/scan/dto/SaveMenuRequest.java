package com.hanspoon.backend_api.domain.scan.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import java.util.UUID;

public record SaveMenuRequest(@NotNull UUID scanId, @NotNull @Min(0) Integer displayOrder) {}
