package com.bellidental.clinical;

import java.util.Optional;
import java.util.UUID;

/** Interfaz pública del módulo clinical: lo único que otros módulos importan. */
public interface ClinicalDirectory {

    Optional<EncounterSummary> findEncounter(UUID encounterId);
}
