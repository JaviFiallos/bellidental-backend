package com.bellidental.appointments;

import com.bellidental.shared.enums.AppointmentSource;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BookAppointmentCommand(
    UUID patientId,
    UUID professionalId,
    Long specialtyId,
    OffsetDateTime startAt,
    AppointmentSource source,
    UUID createdBy,
    String reason
) {
}
