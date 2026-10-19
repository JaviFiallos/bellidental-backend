package com.bellidental.clinicalrecords;

import java.time.LocalDate;
import java.util.UUID;

public record ClinicalRecordSummary(
        UUID id,
        UUID patientId,
        String recordNumber,
        LocalDate openedAt,
        UUID openedByProfessionalId
) {}
