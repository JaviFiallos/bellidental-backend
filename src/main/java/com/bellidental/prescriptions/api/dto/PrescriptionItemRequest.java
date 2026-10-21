package com.bellidental.prescriptions.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PrescriptionItemRequest(
        @NotNull Long medicationId,
        Long medicationPresetId,
        @Size(max = 100) String dose,
        @Size(max = 100) String frequency,
        @Size(max = 100) String duration,
        @Size(max = 50) String quantity,
        String instructions
) {}
