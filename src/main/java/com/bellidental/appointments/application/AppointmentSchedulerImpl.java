package com.bellidental.appointments.application;

import com.bellidental.appointments.AppointmentScheduler;
import com.bellidental.appointments.AppointmentSummary;
import com.bellidental.appointments.AvailableSlot;
import com.bellidental.appointments.BookAppointmentCommand;
import com.bellidental.appointments.api.dto.AppointmentResponse;
import com.bellidental.appointments.api.dto.CreateAppointmentRequest;
import com.bellidental.appointments.api.dto.RescheduleAppointmentRequest;
import com.bellidental.persistence.entity.Appointment;
import com.bellidental.persistence.repository.AppointmentRepository;
import com.bellidental.shared.time.ClinicClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppointmentSchedulerImpl implements AppointmentScheduler {

    private final AppointmentService appointmentService;
    private final AvailabilityService availabilityService;
    private final AppointmentRepository appointmentRepository;
    private final ClinicClock clinicClock;

    @Override
    public Optional<AppointmentSummary> findById(UUID appointmentId) {
        return appointmentRepository.findById(appointmentId).map(this::toSummary);
    }

    @Override
    public List<AvailableSlot> findAvailableSlots(UUID professionalId, Long specialtyId, LocalDate date) {
        return availabilityService.findAvailableSlots(professionalId, specialtyId, date);
    }

    @Override
    public AppointmentSummary book(BookAppointmentCommand command) {
        CreateAppointmentRequest request = new CreateAppointmentRequest(
                command.patientId(), command.professionalId(), command.specialtyId(),
                command.startAt(), null, command.reason(), null);
        return toSummary(appointmentService.book(request, command.source(), command.createdBy()));
    }

    @Override
    public AppointmentSummary reschedule(UUID appointmentId, OffsetDateTime newStartAt, UUID requestedBy) {
        RescheduleAppointmentRequest request = new RescheduleAppointmentRequest(newStartAt, null, null, null);
        return toSummary(appointmentService.reschedule(appointmentId, request, requestedBy));
    }

    @Override
    public void confirm(UUID appointmentId) {
        appointmentService.confirm(appointmentId);
    }

    @Override
    public void cancel(UUID appointmentId) {
        appointmentService.cancel(appointmentId);
    }

    @Override
    public void markAttended(UUID appointmentId) {
        appointmentService.markAttended(appointmentId);
    }

    @Override
    public List<AppointmentSummary> findActiveByPatient(UUID patientId) {
        return appointmentRepository.findActiveFutureByPatient(patientId, clinicClock.now()).stream()
                .map(this::toSummary)
                .toList();
    }

    private AppointmentSummary toSummary(Appointment appointment) {
        return new AppointmentSummary(
                appointment.getId(), appointment.getPatientId(), appointment.getProfessionalId(),
                appointment.getSpecialtyId(), appointment.getStartAt(), appointment.getEndAt(),
                appointment.getStatus(), appointment.getSource(), appointment.getCheckedInAt(),
                appointment.getRescheduledFromId());
    }

    private AppointmentSummary toSummary(AppointmentResponse response) {
        return new AppointmentSummary(
                response.id(), response.patientId(), response.professionalId(),
                response.specialtyId(), response.startAt(), response.endAt(),
                response.status(), response.source(), response.checkedInAt(),
                response.rescheduledFromId());
    }
}
