package com.bellidental.patients;

import com.bellidental.persistence.entity.Patient;
import com.bellidental.shared.enums.BloodType;
import com.bellidental.shared.enums.DocumentType;
import com.bellidental.shared.enums.DocumentType;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
        String sex,
        BloodType bloodType,
        String bloodTypeLabel,
        String phoneE164,
        String email,
        String emergencyContactName,
        String emergencyContactPhoneE164,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PatientResponse from(Patient p) {
        return new PatientResponse(
                p.getId(),
                p.getDocumentType(),
                p.getDocumentNumber(),
                p.getFirstName(),
                p.getLastName(),
                p.getFirstName() + " " + p.getLastName(),
                p.getBirthDate(),
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
