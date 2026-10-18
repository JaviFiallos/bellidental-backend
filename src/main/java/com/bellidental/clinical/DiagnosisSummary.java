package com.bellidental.clinical;

import java.util.UUID;

public record DiagnosisSummary(
        UUID id,
        UUID encounterId,
        String icd10Code,
        String icd10Description,
        boolean voided
) {}
