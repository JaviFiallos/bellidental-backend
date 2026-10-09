package com.bellidental.patients;

import com.bellidental.shared.enums.BloodType;
import com.bellidental.shared.enums.DocumentType;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Datos actualizables de un paciente existente — todos los campos son opcionales.
 */
public record UpdatePatientRequest(

        DocumentType documentType,

        @Size(max = 30)
        String documentNumber,

        @Size(max = 100)
        String firstName,

        @Size(max = 100)
        String lastName,

        @Past(message = "La fecha de nacimiento debe ser una fecha pasada")
        LocalDate birthDate,

        @Size(max = 1)
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

        Boolean active
) {}
