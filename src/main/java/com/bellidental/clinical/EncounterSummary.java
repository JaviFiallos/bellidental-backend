package com.bellidental.clinical;

import com.bellidental.shared.enums.EncounterStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EncounterSummary(
        UUID id,
        UUID appointmentId,
        UUID patientId,
        UUID professionalId,
        EncounterStatus status,
        OffsetDateTime closedAt,
        boolean voided
) {}
