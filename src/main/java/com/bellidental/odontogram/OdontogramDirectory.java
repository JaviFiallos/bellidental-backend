package com.bellidental.odontogram;

import java.util.Optional;
import java.util.UUID;

/** Interfaz pública del módulo odontogram: lo único que otros módulos importan. */
public interface OdontogramDirectory {

    Optional<FindingSummary> findFinding(UUID findingId);

    FindingSummary recordDoneFor(UUID findingId, UUID encounterId);
}
