package com.bellidental.treatments.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record RecordTreatmentRequest(
        @NotNull Long treatmentId,
        @Min(1) Integer quantity,
        @DecimalMin("0.00") BigDecimal price,
        UUID encounterDiagnosisId,
        Short toothCode,
        String surfaceCode,
        String notes
) {}
