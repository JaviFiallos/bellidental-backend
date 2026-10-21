package com.bellidental.clinical;

import com.bellidental.shared.enums.DiagnosisCertainty;

import java.util.UUID;

public record DiagnosisSummary(
        UUID id,
        UUID encounterId,
        String icd10Code,
        String icd10Description,
        boolean voided,
        DiagnosisCertainty certainty,
        String notes
) {}
