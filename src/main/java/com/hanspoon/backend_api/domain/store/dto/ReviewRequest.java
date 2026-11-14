package com.hanspoon.backend_api.domain.store.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record ReviewRequest(UUID scanId, @NotEmpty @Size(max = 30) List<@NotNull @Valid ReviewMenu> menus) {}
