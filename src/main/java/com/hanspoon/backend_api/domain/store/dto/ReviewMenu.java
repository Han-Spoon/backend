package com.hanspoon.backend_api.domain.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record ReviewMenu(
        @NotBlank @Size(max = 200) String name, Map<String, String> answers, @Size(max = 500) String note) {}
