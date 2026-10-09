package com.bellidental.professionals;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interfaz pública del módulo de profesionales para ser consumida por otros módulos.
 */
public interface ProfessionalDirectory {

    Optional<ProfessionalSummary> findById(UUID id);

    List<ProfessionalSummary> findActive();

    boolean hasActiveSpecialty(UUID professionalId, Long specialtyId);

    Optional<ProfessionalSummary> findByUserId(UUID userId);
}
