package com.bellidental.appointments;

import com.bellidental.shared.enums.AppointmentSource;
import com.bellidental.shared.enums.AppointmentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AppointmentSummary(
    UUID id,
    UUID patientId,
    UUID professionalId,
    Long specialtyId,
    OffsetDateTime startAt,
    OffsetDateTime endAt,
    AppointmentStatus status,
    AppointmentSource source,
    OffsetDateTime checkedInAt,
    UUID rescheduledFromId
) {
}
