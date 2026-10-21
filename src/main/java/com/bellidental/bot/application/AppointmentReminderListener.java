package com.bellidental.bot.application;

import com.bellidental.appointments.AppointmentEvent;
import com.bellidental.appointments.AppointmentEventType;
import com.bellidental.appointments.AppointmentScheduler;
import com.bellidental.appointments.AppointmentSummary;
import com.bellidental.persistence.entity.AppointmentReminder;
import com.bellidental.persistence.repository.AppointmentReminderRepository;
import com.bellidental.shared.enums.AppointmentStatus;
import com.bellidental.shared.enums.ReminderStatus;
import com.bellidental.shared.time.ClinicClock;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AppointmentReminderListener {

    private final AppointmentReminderRepository reminderRepository;
    private final AppointmentScheduler appointmentScheduler;
    private final ClinicClock clinicClock;

    @EventListener
    @Transactional
    public void onAppointmentEvent(AppointmentEvent event) {
        if (event.previousAppointmentId() != null) {
            cancelPendingReminders(event.previousAppointmentId());
        }

        Optional<AppointmentSummary> appointmentOpt = appointmentScheduler.findById(event.appointmentId());
        if (appointmentOpt.isEmpty()) {
            return;
        }

        AppointmentSummary appointment = appointmentOpt.get();
        boolean isActive = (appointment.status() == AppointmentStatus.PENDING
                || appointment.status() == AppointmentStatus.CONFIRMED);

        if (!isActive) {
            cancelPendingReminders(appointment.id());
            return;
        }

        if (event.type() == AppointmentEventType.CREATED || event.type() == AppointmentEventType.RESCHEDULED) {
            LocalDate appointmentDate = appointment.startAt().atZoneSameInstant(clinicClock.zone()).toLocalDate();
            LocalDate dayBefore = appointmentDate.minusDays(1);
            OffsetDateTime scheduledAt = dayBefore.atTime(9, 0).atZone(clinicClock.zone()).toOffsetDateTime();

            OffsetDateTime now = clinicClock.now();
            if (scheduledAt.isAfter(now)) {
                boolean alreadyHasPending = reminderRepository.existsByAppointmentIdAndKindAndStatus(
                        appointment.id(),
                        AppointmentReminder.KIND_DAY_BEFORE,
                        ReminderStatus.PENDING
                );
                if (!alreadyHasPending) {
                    AppointmentReminder reminder = new AppointmentReminder(
                            appointment.id(),
                            AppointmentReminder.KIND_DAY_BEFORE,
                            scheduledAt,
                            ReminderStatus.PENDING
                    );
                    reminderRepository.save(reminder);
                }
            }
        }
    }

    private void cancelPendingReminders(UUID appointmentId) {
        List<AppointmentReminder> pending = reminderRepository.findByAppointmentIdAndStatus(
                appointmentId,
                ReminderStatus.PENDING
        );
        if (!pending.isEmpty()) {
            for (AppointmentReminder reminder : pending) {
                reminder.setStatus(ReminderStatus.CANCELLED);
            }
            reminderRepository.saveAll(pending);
        }
    }
}
