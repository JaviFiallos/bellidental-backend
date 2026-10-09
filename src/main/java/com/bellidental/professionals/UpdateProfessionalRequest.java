package com.bellidental.professionals;

import com.bellidental.shared.enums.DocumentType;
import jakarta.validation.constraints.Size;

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

        @Size(max = 7, message = "El color debe ser un código hexadecimal de 7 caracteres (#RRGGBB)")
        String colorTag,

        Boolean active
) {}
