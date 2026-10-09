package com.bellidental.clinicalrecords;

import java.util.Optional;
import java.util.UUID;

public interface ClinicalRecordDirectory {

    Optional<ClinicalRecordSummary> findByPatientId(UUID patientId);

    ClinicalRecordSummary openIfAbsent(UUID patientId, UUID professionalId);
}
