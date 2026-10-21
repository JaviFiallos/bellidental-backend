package com.bellidental.prescriptions;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PrescriptionSummary(
        UUID id,
        UUID encounterId,
        OffsetDateTime prescribedAt,
        String generalInstructions,
        List<PrescriptionItemSummary> items
) {}
