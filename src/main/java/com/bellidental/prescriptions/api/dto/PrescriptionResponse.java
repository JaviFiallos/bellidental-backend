package com.bellidental.prescriptions.api.dto;

import com.bellidental.shared.enums.PrescriptionStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PrescriptionResponse(
        UUID id,
        UUID encounterId,
        UUID encounterDiagnosisId,
        String icd10Code,
        String icd10Description,
        PrescriptionStatus status,
        OffsetDateTime prescribedAt,
        String generalInstructions,
        boolean voided,
        List<PrescriptionItemResponse> items
) {}
