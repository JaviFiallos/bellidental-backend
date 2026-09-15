package com.bellidental.patients;

import com.bellidental.shared.enums.BloodType;
import com.bellidental.shared.enums.DocumentType;
import com.bellidental.shared.enums.Gender;
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
        String emergencyContactPhone,

        Boolean active
) {}
