package com.bellidental.patients;

import com.bellidental.shared.enums.DocumentType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interfaz pública del módulo de pacientes para ser consumida por otros módulos (ej. Citas, Facturación, Bot).
 */
public interface PatientDirectory {

    Optional<PatientSummary> findById(UUID id);

    List<PatientSummary> findActiveByPhone(String phoneE164);

    Optional<PatientSummary> findByDocument(DocumentType type, String documentNumber);

}
