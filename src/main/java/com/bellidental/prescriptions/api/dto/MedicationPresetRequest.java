package com.bellidental.prescriptions.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MedicationPresetRequest(
        @NotBlank @Size(max = 60) String label,
        @NotBlank @Size(max = 100) String dose,
        @NotBlank @Size(max = 100) String frequency,
        @NotBlank @Size(max = 100) String duration,
        String instructions
) {}
