package com.bellidental.professionals;

import com.bellidental.persistence.entity.Professional;
import com.bellidental.persistence.entity.Specialty;
import com.bellidental.shared.enums.DocumentType;

import java.time.OffsetDateTime;
import java.util.List;
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
        String licenseNumber,
        String phoneE164,
        String googleCalendarId,
        String colorTag,
        List<SpecialtyResponse> specialties,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ProfessionalResponse from(Professional p, List<Specialty> specialties) {
        return new ProfessionalResponse(
                p.getId(),
                p.getUser() != null ? p.getUser().getId() : null,
                p.getDocumentType(),
                p.getDocumentNumber(),
                p.getFirstName(),
                p.getLastName(),
                p.getFirstName() + " " + p.getLastName(),
                p.getLicenseNumber(),
                p.getPhoneE164(),
                p.getGoogleCalendarId(),
                p.getColorTag(),
                specialties != null ? specialties.stream().map(SpecialtyResponse::from).toList() : List.of(),
                p.isActive(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
