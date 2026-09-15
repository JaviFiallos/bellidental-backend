package com.bellidental.patients;

import com.bellidental.shared.enums.BloodType;
import com.bellidental.shared.enums.DocumentType;
import com.bellidental.shared.enums.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Datos necesarios para registrar un nuevo paciente.
 */
public record CreatePatientRequest(

        /** ID de cuenta de portal — opcional. */
        UUID userId,

        @NotNull(message = "El tipo de documento es obligatorio")
        DocumentType documentType,

        @NotBlank(message = "El número de documento es obligatorio")
        @Size(max = 30)
        String documentNumber,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100)
        String firstName,

        @NotBlank(message = "El apellido es obligatorio")
        @Size(max = 100)
        String lastName,

        @NotNull(message = "La fecha de nacimiento es obligatoria")
        @Past(message = "La fecha de nacimiento debe ser una fecha pasada")
        LocalDate birthDate,

        Gender gender,

        BloodType bloodType,

        @Size(max = 30)
        String phone,

        @Size(max = 150)
        String email,

        String address,

        @Size(max = 150)
        String emergencyContactName,

        @Size(max = 30)
        String emergencyContactPhone
) {}
