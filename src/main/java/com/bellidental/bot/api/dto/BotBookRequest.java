package com.bellidental.bot.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BotBookRequest(
        @NotBlank(message = "El teléfono es obligatorio.")
        String phone,

        @NotNull(message = "El ID del paciente es obligatorio.")
        UUID patientId,

        @NotNull(message = "El ID de la especialidad es obligatorio.")
        Long specialtyId,

        @NotNull(message = "El ID del profesional es obligatorio.")
        UUID professionalId,

        @NotNull(message = "La fecha y hora de inicio son obligatorias.")
        OffsetDateTime startAt
) {}
