package com.bellidental.patients;

import com.bellidental.persistence.entity.Patient;
import com.bellidental.shared.enums.BloodType;
import com.bellidental.shared.enums.DocumentType;
import com.bellidental.shared.enums.Gender;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Proyección de un paciente para respuestas API.
 */
public record PatientResponse(
        UUID id,
        UUID userId,
        DocumentType documentType,
        String documentNumber,
        String firstName,
        String lastName,
        String fullName,
        LocalDate birthDate,
        Gender gender,
        String genderLabel,
        BloodType bloodType,
        String bloodTypeLabel,
        String phone,
        String email,
        String address,
        String emergencyContactName,
        String emergencyContactPhone,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PatientResponse from(Patient p) {
        return new PatientResponse(
                p.getId(),
                p.getUser() != null ? p.getUser().getId() : null,
                p.getDocumentType(),
                p.getDocumentNumber(),
                p.getFirstName(),
                p.getLastName(),
                p.getFirstName() + " " + p.getLastName(),
                p.getBirthDate(),
                p.getGender(),
                p.getGender() != null ? p.getGender().getLabel() : null,
                p.getBloodType(),
                p.getBloodType() != null ? p.getBloodType().getLabel() : null,
                p.getPhone(),
                p.getEmail(),
                p.getAddress(),
                p.getEmergencyContactName(),
                p.getEmergencyContactPhone(),
                p.isActive(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
