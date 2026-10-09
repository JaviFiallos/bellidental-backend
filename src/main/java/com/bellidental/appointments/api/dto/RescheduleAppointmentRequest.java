package com.bellidental.appointments.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RescheduleAppointmentRequest(
    @NotNull OffsetDateTime startAt,
    OffsetDateTime endAt,
    UUID professionalId,
    Long specialtyId
) {
}
