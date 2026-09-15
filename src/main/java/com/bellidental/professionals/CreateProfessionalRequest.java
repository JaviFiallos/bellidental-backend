package com.bellidental.professionals;

import com.bellidental.shared.enums.DocumentType;
import com.bellidental.shared.enums.Specialty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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

        @NotNull(message = "La especialidad es obligatoria")
        Specialty specialty,

        @NotBlank(message = "El número de licencia es obligatorio")
        @Size(max = 50)
        String licenseNumber,

        @Size(max = 30)
        String phone,

        @Size(max = 150)
        String email,

        /** Color hexadecimal para el calendario (#RRGGBB). */
        @Size(max = 7, message = "El color debe ser un código hexadecimal de 7 caracteres (#RRGGBB)")
        String colorTag
) {}
