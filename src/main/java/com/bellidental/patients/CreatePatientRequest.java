package com.bellidental.patients;

import com.bellidental.shared.enums.BloodType;
import com.bellidental.shared.enums.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Datos necesarios para registrar un nuevo paciente.
 */
public record CreatePatientRequest(


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

        @NotBlank(message = "El sexo es obligatorio")
        @Pattern(regexp = "^[MF]$", message = "El sexo debe ser M o F")
        String sex,

        BloodType bloodType,

        @Size(max = 15)
        String phoneE164,

        @Size(max = 150)
        String email,

        @Size(max = 150)
        String emergencyContactName,

        @Size(max = 15)
        String emergencyContactPhoneE164,

        @Size(max = 1000)
        String adminNotes
) {}
