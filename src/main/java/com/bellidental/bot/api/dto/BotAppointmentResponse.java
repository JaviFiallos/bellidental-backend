package com.bellidental.bot.api.dto;

import com.bellidental.shared.enums.AppointmentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BotAppointmentResponse(
        UUID appointmentId,
        Long specialtyId,
        String specialtyName,
        UUID professionalId,
        String professionalName,
        OffsetDateTime startAt,
        AppointmentStatus status
) {}
