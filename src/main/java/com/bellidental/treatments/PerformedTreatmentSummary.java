package com.bellidental.treatments;

import java.math.BigDecimal;
import java.util.UUID;

public record PerformedTreatmentSummary(
        UUID id,
        UUID encounterId,
        String treatmentCode,
        String treatmentName,
        Short toothCode,
        String surfaceCode,
        Integer quantity,
        BigDecimal price
) {}
