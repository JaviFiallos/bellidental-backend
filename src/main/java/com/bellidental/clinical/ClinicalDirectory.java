package com.bellidental.clinical;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Interfaz pública del módulo clinical: lo único que otros módulos importan. */
public interface ClinicalDirectory {

    Optional<EncounterSummary> findEncounter(UUID encounterId);

    EncounterSummary requireModifiableEncounter(UUID encounterId);

    Optional<DiagnosisSummary> findDiagnosis(UUID encounterDiagnosisId);

    List<EncounterSummary> findPatientEncounters(UUID patientId);

    List<DiagnosisSummary> findDiagnoses(Collection<UUID> encounterIds);
}
