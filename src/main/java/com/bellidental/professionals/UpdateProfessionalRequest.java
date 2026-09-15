package com.bellidental.professionals;

import com.bellidental.shared.enums.DocumentType;
import com.bellidental.shared.enums.Specialty;
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

        Specialty specialty,

        @Size(max = 50)
        String licenseNumber,

        @Size(max = 30)
        String phone,

        @Size(max = 150)
        String email,

        @Size(max = 7, message = "El color debe ser un código hexadecimal de 7 caracteres (#RRGGBB)")
        String colorTag,

        Boolean active
) {}
