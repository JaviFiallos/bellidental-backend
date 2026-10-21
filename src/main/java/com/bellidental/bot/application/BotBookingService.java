package com.bellidental.bot.application;

import com.bellidental.appointments.AppointmentScheduler;
import com.bellidental.appointments.AppointmentSummary;
import com.bellidental.appointments.BookAppointmentCommand;
import com.bellidental.bot.api.dto.BotAppointmentResponse;
import com.bellidental.bot.api.dto.BotBookRequest;
import com.bellidental.bot.api.dto.BotRescheduleRequest;
import com.bellidental.bot.domain.BotBookingLimits;
import com.bellidental.config.BotProperties;
import com.bellidental.patients.PatientDirectory;
import com.bellidental.patients.PatientSummary;
import com.bellidental.professionals.ProfessionalDirectory;
import com.bellidental.professionals.SpecialtySummary;
import com.bellidental.security.CurrentUser;
import com.bellidental.shared.enums.AppointmentSource;
import com.bellidental.shared.enums.AppointmentStatus;
import com.bellidental.shared.exception.BusinessException;
import com.bellidental.shared.exception.ResourceNotFoundException;
import com.bellidental.shared.time.ClinicClock;
import com.bellidental.shared.validation.PhoneNumberNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BotBookingService {

    public static final String CODE_TOO_LATE = "BOT_TOO_LATE";

    private final AppointmentScheduler appointmentScheduler;
    private final PatientDirectory patientDirectory;
    private final BotIdentityService botIdentityService;
    private final ProfessionalDirectory professionalDirectory;
    private final CurrentUser currentUser;
    private final ClinicClock clinicClock;
    private final BotProperties botProperties;

    @Transactional
    public BotAppointmentResponse bookAppointment(BotBookRequest request) {
        botIdentityService.requirePatientOfPhone(request.phone(), request.patientId());

        String normalizedPhone = PhoneNumberNormalizer.normalize(request.phone());
        List<PatientSummary> phonePatients = patientDirectory.findActiveByPhone(normalizedPhone);
        List<AppointmentSummary> phoneAppointments = phonePatients.stream()
                .flatMap(p -> appointmentScheduler.findActiveByPatient(p.id()).stream())
                .toList();

        int maxActive = botProperties.limits().maxActivePerPhone();
        BotBookingLimits.check(phoneAppointments, request.patientId(), request.specialtyId(), maxActive);

        UUID botUserId = currentUser.userId();
        BookAppointmentCommand command = new BookAppointmentCommand(
                request.patientId(),
                request.professionalId(),
                request.specialtyId(),
                request.startAt(),
                AppointmentSource.BOT,
                botUserId,
                null
        );

        AppointmentSummary booked = appointmentScheduler.book(command);
        return toBotAppointmentResponse(booked);
    }

    @Transactional
    public BotAppointmentResponse rescheduleAppointment(UUID appointmentId, BotRescheduleRequest request) {
        AppointmentSummary original = requireAppointmentOfPhone(appointmentId, request.phone());
        validateChangeAdvanceNotice(original.startAt(), "reprogramar");

        UUID botUserId = currentUser.userId();
        AppointmentSummary rescheduled = appointmentScheduler.reschedule(appointmentId, request.startAt(), botUserId);
        return toBotAppointmentResponse(rescheduled);
    }

    @Transactional
    public void cancelAppointment(UUID appointmentId, String phone) {
        AppointmentSummary appointment = requireAppointmentOfPhone(appointmentId, phone);
        validateChangeAdvanceNotice(appointment.startAt(), "cancelar");

        appointmentScheduler.cancel(appointmentId);
    }

    @Transactional
    public void confirmAppointment(UUID appointmentId, String phone) {
        AppointmentSummary appointment = requireAppointmentOfPhone(appointmentId, phone);
        if (appointment.status() == AppointmentStatus.CONFIRMED) {
            return;
        }
        appointmentScheduler.confirm(appointmentId);
    }

    private AppointmentSummary requireAppointmentOfPhone(UUID appointmentId, String phone) {
        AppointmentSummary appointment = appointmentScheduler.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada."));

        String normalizedPhone = PhoneNumberNormalizer.normalize(phone);
        List<PatientSummary> phonePatients = patientDirectory.findActiveByPhone(normalizedPhone);
        boolean patientBelongs = phonePatients.stream()
                .anyMatch(p -> p.id().equals(appointment.patientId()));

        if (!patientBelongs) {
            throw new ResourceNotFoundException("Cita no encontrada.");
        }

        return appointment;
    }

    private void validateChangeAdvanceNotice(OffsetDateTime appointmentStartAt, String operation) {
        int minHours = botProperties.limits().changeMinHours();
        OffsetDateTime now = clinicClock.now();
        if (now.plusHours(minHours).isAfter(appointmentStartAt)) {
            throw new BusinessException(
                    "No se puede " + operation + " la cita con menos de " + minHours + " horas de anticipación.",
                    CODE_TOO_LATE
            );
        }
    }

    private BotAppointmentResponse toBotAppointmentResponse(AppointmentSummary summary) {
        String specialtyName = professionalDirectory.findSpecialty(summary.specialtyId())
                .map(SpecialtySummary::name)
                .orElse("");
        String professionalName = professionalDirectory.findById(summary.professionalId())
                .map(p -> (p.firstName() + " " + p.lastName()).trim())
                .orElse("");
        return new BotAppointmentResponse(
                summary.id(),
                summary.specialtyId(),
                specialtyName,
                summary.professionalId(),
                professionalName,
                summary.startAt(),
                summary.status()
        );
    }
}
