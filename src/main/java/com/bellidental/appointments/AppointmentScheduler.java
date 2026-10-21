package com.bellidental.appointments;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Interfaz pública del módulo de citas: lo único que otros módulos (atención clínica, bot) importan. */
public interface AppointmentScheduler {

    Optional<AppointmentSummary> findById(UUID appointmentId);

    List<AvailableSlot> findAvailableSlots(UUID professionalId, Long specialtyId, LocalDate date);

    AppointmentSummary book(BookAppointmentCommand command);

    AppointmentSummary reschedule(UUID appointmentId, OffsetDateTime newStartAt, UUID requestedBy);

    void confirm(UUID appointmentId);

    void cancel(UUID appointmentId);

    void markAttended(UUID appointmentId);

    List<AppointmentSummary> findActiveByPatient(UUID patientId);
}
