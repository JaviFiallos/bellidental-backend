package com.bellidental.appointments.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateAppointmentRequest(
    @NotNull UUID patientId,
    @NotNull UUID professionalId,
    @NotNull Long specialtyId,
    @NotNull OffsetDateTime startAt,
    OffsetDateTime endAt,
    @Size(max = 255) String reason,
    String notes
) {
}
