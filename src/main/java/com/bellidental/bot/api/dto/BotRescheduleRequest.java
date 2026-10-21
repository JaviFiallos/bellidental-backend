package com.bellidental.bot.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record BotRescheduleRequest(
        @NotBlank(message = "El teléfono es obligatorio.")
        String phone,

        @NotNull(message = "La nueva fecha y hora de inicio son obligatorias.")
        OffsetDateTime startAt
) {}
