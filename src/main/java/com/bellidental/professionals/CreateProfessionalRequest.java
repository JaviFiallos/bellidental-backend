package com.bellidental.professionals;

import com.bellidental.shared.enums.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;

/**
 * Datos necesarios para crear un nuevo profesional.
 */
public record CreateProfessionalRequest(

        /** ID de la cuenta de usuario del sistema a vincular (opcional). */
        UUID userId,

        @NotNull(message = "El tipo de documento es obligatorio")
        DocumentType documentType,

        @NotBlank(message = "El número de documento es obligatorio")
        @Size(max = 30, message = "El número de documento no debe superar los 30 caracteres")
        String documentNumber,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100)
        String firstName,

        @NotBlank(message = "El apellido es obligatorio")
        @Size(max = 100)
        String lastName,

        @NotBlank(message = "El número de licencia es obligatorio")
        @Size(max = 50)
        String licenseNumber,

        @Size(max = 15)
        String phoneE164,

        @Size(max = 255)
        String googleCalendarId,

        /** Color hexadecimal para el calendario (#RRGGBB). */
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "El color debe ser un código hexadecimal de 7 caracteres (#RRGGBB)")
        String colorTag,

        List<Long> specialtyIds
) {}
