package com.bellidental.appointments.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WalkInRequest(
    @NotNull UUID patientId,
    @NotNull UUID professionalId,
    @NotNull Long specialtyId,
    OffsetDateTime startAt,
    OffsetDateTime endAt
) {
    public WalkInRequest(UUID patientId, UUID professionalId, Long specialtyId) {
        this(patientId, professionalId, specialtyId, null, null);
    }
}
