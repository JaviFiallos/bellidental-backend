package com.bellidental.appointments;

import java.util.UUID;

public record AppointmentEvent(
    UUID appointmentId,
    AppointmentEventType type,
    UUID previousAppointmentId
) {
}
