package com.bellidental.treatments.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PerformedTreatmentResponse(
        UUID id,
        UUID encounterId,
        Long treatmentId,
        String treatmentCode,
        String treatmentName,
        UUID encounterDiagnosisId,
        UUID dentalFindingId,
        Short toothCode,
        String surfaceCode,
        Integer quantity,
        BigDecimal price,
        BigDecimal total,
        String notes,
        OffsetDateTime createdAt
) {}
