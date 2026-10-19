package com.bellidental.bot.application;

import com.bellidental.appointments.AppointmentScheduler;
import com.bellidental.appointments.AppointmentSummary;
import com.bellidental.bot.api.dto.DueReminderResponse;
import com.bellidental.bot.api.dto.ReminderReplyRequest;
import com.bellidental.bot.api.dto.ReminderReplyResponse;
import com.bellidental.bot.api.dto.ReminderSentRequest;
import com.bellidental.config.BotProperties;
import com.bellidental.patients.PatientDirectory;
import com.bellidental.patients.PatientSummary;
import com.bellidental.persistence.entity.AppointmentReminder;
import com.bellidental.persistence.repository.AppointmentReminderRepository;
import com.bellidental.persistence.repository.PrivacyConsentRepository;
import com.bellidental.professionals.ProfessionalDirectory;
import com.bellidental.shared.enums.AppointmentStatus;
import com.bellidental.shared.enums.ReminderStatus;
import com.bellidental.shared.exception.BusinessException;
import com.bellidental.shared.exception.ResourceNotFoundException;
import com.bellidental.shared.time.ClinicClock;
import com.bellidental.shared.validation.PhoneNumberNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BotReminderService {

    private final AppointmentReminderRepository reminderRepository;
    private final AppointmentScheduler appointmentScheduler;
    private final PatientDirectory patientDirectory;
    private final ProfessionalDirectory professionalDirectory;
    private final PrivacyConsentRepository privacyConsentRepository;
    private final ClinicClock clinicClock;
    private final BotProperties botProperties;

    @Transactional(readOnly = true)
    public List<DueReminderResponse> getDueReminders() {
        OffsetDateTime now = clinicClock.now();
        List<AppointmentReminder> pendingReminders = reminderRepository
                .findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(ReminderStatus.PENDING, now);

        List<DueReminderResponse> result = new ArrayList<>();
        String currentNoticeVersion = botProperties.privacyNoticeVersion();

        for (AppointmentReminder reminder : pendingReminders) {
            if (result.size() >= 50) {
                break;
            }

            Optional<AppointmentSummary> apptOpt = appointmentScheduler.findById(reminder.getAppointmentId());
            if (apptOpt.isEmpty()) {
                continue;
            }
            AppointmentSummary appt = apptOpt.get();

            boolean isActive = (appt.status() == AppointmentStatus.PENDING
                    || appt.status() == AppointmentStatus.CONFIRMED);
            if (!isActive || !appt.startAt().isAfter(now)) {
                continue;
            }

            Optional<PatientSummary> patientOpt = patientDirectory.findById(appt.patientId());
            if (patientOpt.isEmpty()) {
                continue;
            }
            PatientSummary patient = patientOpt.get();
            if (patient.phoneE164() == null || patient.phoneE164().isBlank()) {
                continue;
            }

            String normalizedPhone = PhoneNumberNormalizer.normalize(patient.phoneE164());
            boolean hasConsent = privacyConsentRepository.existsByPhoneE164AndNoticeVersion(
                    normalizedPhone,
                    currentNoticeVersion
            );
            if (!hasConsent) {
                continue;
            }

            String professionalName = professionalDirectory.findById(appt.professionalId())
                    .map(p -> (p.firstName() + " " + p.lastName()).trim())
                    .orElse("");

            String patientFirstName = patient.firstName() != null
                    ? patient.firstName().trim().split("\\s+")[0]
                    : "";

            result.add(new DueReminderResponse(
                    reminder.getId(),
                    appt.id(),
                    normalizedPhone,
                    patientFirstName,
                    professionalName,
                    appt.startAt()
            ));
        }

        return result;
    }

    @Transactional
    public void markSent(UUID reminderId, ReminderSentRequest request) {
        AppointmentReminder reminder = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new ResourceNotFoundException("Recordatorio no encontrado."));

        if (reminder.getStatus() != ReminderStatus.PENDING) {
            throw new BusinessException("Solo se pueden marcar como enviados los recordatorios pendientes.");
        }

        reminder.setStatus(ReminderStatus.SENT);
        reminder.setSentAt(clinicClock.now());
        reminder.setWaMessageId(request.waMessageId().trim());
        reminderRepository.save(reminder);
    }

    @Transactional
    public void markFailed(UUID reminderId) {
        AppointmentReminder reminder = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new ResourceNotFoundException("Recordatorio no encontrado."));

        if (reminder.getStatus() != ReminderStatus.PENDING) {
            throw new BusinessException("Solo se pueden marcar como fallidos los recordatorios pendientes.");
        }

        reminder.setStatus(ReminderStatus.FAILED);
        reminderRepository.save(reminder);
    }

    @Transactional
    public ReminderReplyResponse handleReply(ReminderReplyRequest request) {
        AppointmentReminder reminder = reminderRepository.findByWaMessageId(request.waMessageId().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Recordatorio no encontrado."));

        if (reminder.getStatus() != ReminderStatus.SENT) {
            throw new ResourceNotFoundException("Recordatorio no encontrado.");
        }

        AppointmentSummary appointment = appointmentScheduler.findById(reminder.getAppointmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada."));

        PatientSummary patient = patientDirectory.findById(appointment.patientId())
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado."));

        String reqPhoneNormalized = PhoneNumberNormalizer.normalize(request.phone());
        String patientPhoneNormalized = patient.phoneE164() != null
                ? PhoneNumberNormalizer.normalize(patient.phoneE164())
                : "";

        if (!reqPhoneNormalized.equals(patientPhoneNormalized)) {
            throw new ResourceNotFoundException("Recordatorio no encontrado.");
        }

        // Siempre se guarda el estado RESPONDED, la respuesta y responded_at (D-2)
        reminder.setStatus(ReminderStatus.RESPONDED);
        reminder.setResponse(request.response());
        reminder.setRespondedAt(clinicClock.now());
        reminderRepository.save(reminder);

        int minHours = botProperties.limits().changeMinHours();
        OffsetDateTime now = clinicClock.now();
        boolean isTooLate = now.plusHours(minHours).isAfter(appointment.startAt());

        String outcome = switch (request.response()) {
            case CONFIRMED -> {
                if (appointment.status() != AppointmentStatus.CONFIRMED) {
                    appointmentScheduler.confirm(appointment.id());
                }
                yield "CONFIRMED";
            }
            case CANCELLED -> {
                if (isTooLate) {
                    yield "TOO_LATE";
                } else {
                    appointmentScheduler.cancel(appointment.id());
                    yield "CANCELLED";
                }
            }
            case RESCHEDULED -> {
                if (isTooLate) {
                    yield "TOO_LATE";
                } else {
                    yield "RESCHEDULE_REQUESTED";
                }
            }
        };

        return new ReminderReplyResponse(outcome, appointment.id(), patient.id());
    }
}
