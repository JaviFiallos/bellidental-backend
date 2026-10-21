package com.bellidental.clinical.api.dto;

import jakarta.validation.constraints.NotBlank;

public record VoidEncounterRequest(
        @NotBlank String voidReason
) {}
