package com.bellidental.patients;

import com.bellidental.shared.enums.DocumentType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Elemento de la lista de pacientes con última atención y saldo pendiente.
 */
public record PatientListItem(
        UUID id,
        String firstName,
        String lastName,
        String fullName,
        DocumentType documentType,
        String documentNumber,
        Integer age,
        OffsetDateTime lastEncounterAt,
        OffsetDateTime createdAt,
        BigDecimal pendingBalance
) {}
