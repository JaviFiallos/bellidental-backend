package com.bellidental.professionals;

import com.bellidental.shared.enums.DocumentType;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * Datos actualizables de un profesional existente — todos los campos son opcionales.
 */
public record UpdateProfessionalRequest(

        DocumentType documentType,

        @Size(max = 30)
        String documentNumber,

        @Size(max = 100)
        String firstName,

        @Size(max = 100)
        String lastName,

        @Size(max = 50)
        String licenseNumber,

        @Size(max = 15)
        String phoneE164,

        @Size(max = 255)
        String googleCalendarId,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "El color debe ser un código hexadecimal de 7 caracteres (#RRGGBB)")
        String colorTag,

        List<Long> specialtyIds
) {}
