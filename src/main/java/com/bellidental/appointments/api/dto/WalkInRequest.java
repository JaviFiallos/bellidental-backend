package com.bellidental.appointments.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record WalkInRequest(
    @NotNull UUID patientId,
    @NotNull UUID professionalId,
    @NotNull Long specialtyId
) {
}
