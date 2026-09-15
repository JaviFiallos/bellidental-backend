package com.bellidental.professionals;

import com.bellidental.persistence.entity.Professional;
import com.bellidental.shared.enums.DocumentType;
import com.bellidental.shared.enums.Specialty;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Proyección de un profesional para respuestas API.
 */
public record ProfessionalResponse(
        UUID id,
        UUID userId,
        DocumentType documentType,
        String documentNumber,
        String firstName,
        String lastName,
        String fullName,
        Specialty specialty,
        String specialtyLabel,
        String licenseNumber,
        String phone,
        String email,
        String colorTag,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ProfessionalResponse from(Professional p) {
        return new ProfessionalResponse(
                p.getId(),
                p.getUser() != null ? p.getUser().getId() : null,
                p.getDocumentType(),
                p.getDocumentNumber(),
                p.getFirstName(),
                p.getLastName(),
                p.getFirstName() + " " + p.getLastName(),
                p.getSpecialty(),
                p.getSpecialty().getLabel(),
                p.getLicenseNumber(),
                p.getPhone(),
                p.getEmail(),
                p.getColorTag(),
                p.isActive(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
