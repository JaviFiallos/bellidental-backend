package com.bellidental.prescriptions.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MedicationRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 100) String presentation,
        @NotBlank @Size(max = 100) String concentration,
        @Size(max = 50) String route
) {}
