package com.bellidental.clinicalrecords;

import java.util.UUID;

public record ClinicalRecordSummary(
        UUID id,
        UUID patientId,
        String recordNumber
) {}
