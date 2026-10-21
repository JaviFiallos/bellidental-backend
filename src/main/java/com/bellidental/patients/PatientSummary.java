package com.bellidental.patients;

import com.bellidental.shared.enums.DocumentType;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Representación pública de un paciente para otros módulos.
 */
public record PatientSummary(
        UUID id,
        DocumentType documentType,
        String documentNumber,
        String firstName,
        String lastName,
        LocalDate birthDate,
        String sex,
        String phoneE164,
        boolean active
) {}
