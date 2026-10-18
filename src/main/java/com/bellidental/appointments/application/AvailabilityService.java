package com.bellidental.appointments.application;

import com.bellidental.appointments.AvailableSlot;
import com.bellidental.appointments.domain.AppointmentDuration;
import com.bellidental.appointments.domain.EffectiveScheduleResolver;
import com.bellidental.appointments.domain.Shift;
import com.bellidental.appointments.domain.SlotCalculator;
import com.bellidental.appointments.domain.TimeRange;
import com.bellidental.persistence.repository.AppointmentRepository;
import com.bellidental.persistence.repository.ProfessionalScheduleRepository;
import com.bellidental.professionals.ProfessionalDirectory;
import com.bellidental.professionals.SpecialtySummary;
import com.bellidental.shared.exception.BusinessException;
import com.bellidental.shared.exception.ResourceNotFoundException;
import com.bellidental.shared.time.ClinicClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final AppointmentRepository appointmentRepository;
    private final ProfessionalScheduleRepository scheduleRepository;
    private final ProfessionalDirectory professionalDirectory;
    private final ClinicClock clock;

    @Transactional(readOnly = true)
    public List<AvailableSlot> findAvailableSlots(UUID professionalId, Long specialtyId, LocalDate date) {
        return findAvailableSlots(professionalId, specialtyId, date, null);
    }

    @Transactional(readOnly = true)
    public List<AvailableSlot> findAvailableSlots(UUID professionalId, Long specialtyId, LocalDate date, Integer durationMinutes) {
        if (durationMinutes != null) {
            AppointmentDuration.validate(durationMinutes);
        }
        SpecialtySummary specialty = professionalDirectory.findSpecialty(specialtyId)
                .filter(SpecialtySummary::active)
                .orElseThrow(() -> new ResourceNotFoundException("Especialidad no encontrada o inactiva."));
        if (!professionalDirectory.hasActiveSpecialty(professionalId, specialtyId)) {
            throw new BusinessException("El profesional no tiene activa esta especialidad.");
        }
        if (date.isBefore(clock.today())) {
            return List.of();
        }

        int duration = durationMinutes != null ? durationMinutes : specialty.defaultDurationMinutes();

        List<Shift> shifts = EffectiveScheduleResolver.resolve(date,
                scheduleRepository.findByProfessionalIdAndActiveTrue(professionalId).stream()
                        .map(ScheduleService::toRule)
                        .toList());

        OffsetDateTime dayStart = date.atStartOfDay(clock.zone()).toOffsetDateTime();
        OffsetDateTime dayEnd = date.plusDays(1).atStartOfDay(clock.zone()).toOffsetDateTime();

        return SlotCalculator.calculate(date, shifts, duration,
                findBusyRanges(professionalId, dayStart, dayEnd), clock.now(), clock.zone());
    }

    @Transactional(readOnly = true)
    public List<TimeRange> findBusyRanges(UUID professionalId, OffsetDateTime from, OffsetDateTime to) {
        return appointmentRepository.findActiveOverlapping(professionalId, from, to).stream()
                .map(appointment -> new TimeRange(appointment.getStartAt(), appointment.getEndAt()))
                .toList();
    }
}
