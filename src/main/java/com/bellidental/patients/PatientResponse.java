package com.bellidental.patients;

import com.bellidental.persistence.entity.Patient;
import com.bellidental.shared.enums.BloodType;
import com.bellidental.shared.enums.DocumentType;
import com.bellidental.shared.enums.DocumentType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Proyección de un paciente para respuestas API.
 */
public record PatientResponse(
        UUID id,
        DocumentType documentType,
        String documentNumber,
        String firstName,
        String lastName,
        String fullName,
        LocalDate birthDate,
        Integer age,
        String sex,
        BloodType bloodType,
        String bloodTypeLabel,
        String phoneE164,
        String email,
        String emergencyContactName,
        String emergencyContactPhoneE164,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static PatientResponse from(Patient p, ZoneId clinicZone) {
        Integer age = null;
        if (p.getBirthDate() != null) {
            age = Period.between(p.getBirthDate(), LocalDate.now(clinicZone)).getYears();
        }

        return new PatientResponse(
                p.getId(),
                p.getDocumentType(),
                p.getDocumentNumber(),
                p.getFirstName(),
                p.getLastName(),
                p.getFirstName() + " " + p.getLastName(),
                p.getBirthDate(),
                age,
                p.getSex(),
                p.getBloodType(),
                p.getBloodType() != null ? p.getBloodType().getLabel() : null,
                p.getPhoneE164(),
                p.getEmail(),
                p.getEmergencyContactName(),
                p.getEmergencyContactPhoneE164(),
                p.isActive(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
