package com.bellidental.treatments.api.dto;

import jakarta.validation.constraints.NotBlank;

public record VoidTreatmentRequest(
        @NotBlank String voidReason
) {}
