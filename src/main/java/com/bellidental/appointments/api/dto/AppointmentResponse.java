package com.bellidental.appointments.api.dto;

import com.bellidental.shared.enums.AppointmentSource;
import com.bellidental.shared.enums.AppointmentStatus;
import com.bellidental.shared.enums.EncounterStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AppointmentResponse(
    UUID id,
    UUID patientId,
    String patientName,
    UUID professionalId,
    String professionalName,
    String colorTag,
    Long specialtyId,
    String specialtyName,
    OffsetDateTime startAt,
    OffsetDateTime endAt,
    AppointmentStatus status,
    AppointmentSource source,
    String reason,
    String notes,
    OffsetDateTime checkedInAt,
    UUID rescheduledFromId,
    UUID encounterId,
    EncounterStatus encounterStatus,
    boolean encounterVoided
) {
}
