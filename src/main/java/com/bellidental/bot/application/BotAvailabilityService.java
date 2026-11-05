package com.bellidental.bot.application;

import com.bellidental.appointments.AppointmentScheduler;
import com.bellidental.appointments.AvailableSlot;
import com.bellidental.appointments.EffectiveDay;
import com.bellidental.bot.api.dto.BotProfessionalResponse;
import com.bellidental.bot.api.dto.BotProfessionalScheduleResponse;
import com.bellidental.bot.api.dto.BotProfessionalScheduleResponse.ScheduleDay;
import com.bellidental.bot.api.dto.BotProfessionalScheduleResponse.ScheduleShift;
import com.bellidental.bot.api.dto.BotSlotResponse;
import com.bellidental.bot.api.dto.BotSpecialtyResponse;
import com.bellidental.bot.api.dto.UrgentSlotsResponse;
import com.bellidental.config.BotProperties;
import com.bellidental.professionals.ProfessionalDirectory;
import com.bellidental.professionals.ProfessionalSummary;
import com.bellidental.professionals.SpecialtySummary;
import com.bellidental.shared.exception.InvalidInputException;
import com.bellidental.shared.exception.ResourceNotFoundException;
import com.bellidental.shared.time.ClinicClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BotAvailabilityService {

    private final AppointmentScheduler appointmentScheduler;
    private final ProfessionalDirectory professionalDirectory;
    private final BotProperties botProperties;
    private final ClinicClock clinicClock;

    public List<BotSpecialtyResponse> getActiveSpecialties() {
        return professionalDirectory.findActiveSpecialties().stream()
                .filter(s -> !professionalDirectory.findActiveBySpecialty(s.id()).isEmpty())
                .map(s -> new BotSpecialtyResponse(s.id(), s.name(), s.defaultDurationMinutes()))
                .toList();
    }

    public List<BotProfessionalResponse> getActiveProfessionalsBySpecialty(Long specialtyId) {
        requireActiveSpecialty(specialtyId);
        return professionalDirectory.findActiveBySpecialty(specialtyId).stream()
                .map(p -> new BotProfessionalResponse(p.id(), (p.firstName() + " " + p.lastName()).trim()))
                .toList();
    }

    public List<BotProfessionalScheduleResponse> getSchedule(LocalDate from, LocalDate to, Long specialtyId, UUID professionalId) {
        validateScheduleRange(from, to);
        return findScheduledProfessionals(specialtyId, professionalId).stream()
                .map(p -> toProfessionalSchedule(p, from, to))
                .sorted(Comparator.comparing(BotProfessionalScheduleResponse::professionalName))
                .toList();
    }

    public List<LocalDate> getAvailableDays(Long specialtyId, UUID professionalId) {
        requireActiveSpecialty(specialtyId);
        if (professionalId != null) {
            requireActiveProfessionalWithSpecialty(professionalId, specialtyId);
        }

        LocalDate today = clinicClock.today();
        List<LocalDate> daysWithSlots = new ArrayList<>();

        for (int i = 0; i < 14; i++) {
            LocalDate date = today.plusDays(i);
            List<BotSlotResponse> slots = getSlotsForDate(specialtyId, professionalId, date);
            if (!slots.isEmpty()) {
                daysWithSlots.add(date);
            }
        }

        return daysWithSlots;
    }

    public List<BotSlotResponse> getAvailableSlots(Long specialtyId, UUID professionalId, LocalDate date) {
        requireActiveSpecialty(specialtyId);
        if (professionalId != null) {
            requireActiveProfessionalWithSpecialty(professionalId, specialtyId);
        }

        List<BotSlotResponse> slots = getSlotsForDate(specialtyId, professionalId, date);
        return slots.stream().limit(10).toList();
    }

    public UrgentSlotsResponse getUrgentSlots() {
        Long urgentSpecialtyId = botProperties.urgentSpecialtyId();
        requireActiveSpecialty(urgentSpecialtyId);

        LocalDate today = clinicClock.today();
        LocalDate tomorrow = today.plusDays(1);
        OffsetDateTime minStart = clinicClock.now().plusMinutes(30);

        List<BotSlotResponse> combined = new ArrayList<>();
        combined.addAll(getSlotsForDate(urgentSpecialtyId, null, today));
        combined.addAll(getSlotsForDate(urgentSpecialtyId, null, tomorrow));

        List<BotSlotResponse> validSlots = combined.stream()
                .filter(s -> !s.startAt().isBefore(minStart))
                .sorted(Comparator.comparing(BotSlotResponse::startAt))
                .toList();

        List<BotSlotResponse> deduped = deduplicateByStartAt(validSlots);
        List<BotSlotResponse> top3 = deduped.stream().limit(3).toList();

        boolean noSlotsToday = top3.stream().noneMatch(s -> s.startAt().toLocalDate().equals(today));

        return new UrgentSlotsResponse(urgentSpecialtyId, noSlotsToday, top3);
    }

    private List<BotSlotResponse> getSlotsForDate(Long specialtyId, UUID professionalId, LocalDate date) {
        List<BotSlotResponse> result = new ArrayList<>();

        if (professionalId != null) {
            ProfessionalSummary p = professionalDirectory.findById(professionalId)
                    .orElseThrow(() -> new ResourceNotFoundException("Profesional no encontrado."));
            String professionalName = (p.firstName() + " " + p.lastName()).trim();
            List<AvailableSlot> slots = appointmentScheduler.findAvailableSlots(professionalId, specialtyId, date);
            for (AvailableSlot s : slots) {
                result.add(new BotSlotResponse(professionalId, professionalName, s.startAt(), s.endAt()));
            }
        } else {
            List<ProfessionalSummary> professionals = professionalDirectory.findActiveBySpecialty(specialtyId);
            for (ProfessionalSummary p : professionals) {
                String professionalName = (p.firstName() + " " + p.lastName()).trim();
                List<AvailableSlot> slots = appointmentScheduler.findAvailableSlots(p.id(), specialtyId, date);
                for (AvailableSlot s : slots) {
                    result.add(new BotSlotResponse(p.id(), professionalName, s.startAt(), s.endAt()));
                }
            }
        }

        result.sort(Comparator.comparing(BotSlotResponse::startAt));
        return deduplicateByStartAt(result);
    }

    private void validateScheduleRange(LocalDate from, LocalDate to) {
        LocalDate today = clinicClock.today();
        boolean valid = !from.isBefore(today) && !to.isAfter(today.plusDays(13)) && !from.isAfter(to);
        if (!valid) {
            throw new InvalidInputException("Rango de fechas no permitido.");
        }
    }

    private List<ProfessionalSummary> findScheduledProfessionals(Long specialtyId, UUID professionalId) {
        List<ProfessionalSummary> professionals = specialtyId != null
                ? professionalDirectory.findActiveBySpecialty(specialtyId)
                : professionalDirectory.findActive();
        return professionals.stream()
                .filter(p -> professionalId == null || p.id().equals(professionalId))
                .toList();
    }

    private BotProfessionalScheduleResponse toProfessionalSchedule(ProfessionalSummary p, LocalDate from, LocalDate to) {
        List<ScheduleDay> days = appointmentScheduler.findEffectiveSchedule(p.id(), from, to).stream()
                .map(this::toScheduleDay)
                .toList();
        return new BotProfessionalScheduleResponse(p.id(), (p.firstName() + " " + p.lastName()).trim(), days);
    }

    private ScheduleDay toScheduleDay(EffectiveDay day) {
        List<ScheduleShift> shifts = day.shifts().stream()
                .map(shift -> new ScheduleShift(formatTime(shift.startTime()), formatTime(shift.endTime())))
                .toList();
        return new ScheduleDay(day.date(), shifts);
    }

    private String formatTime(LocalTime time) {
        return time.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT));
    }

    private List<BotSlotResponse> deduplicateByStartAt(List<BotSlotResponse> slots) {
        Set<OffsetDateTime> seen = new HashSet<>();
        List<BotSlotResponse> deduped = new ArrayList<>();
        for (BotSlotResponse slot : slots) {
            if (seen.add(slot.startAt())) {
                deduped.add(slot);
            }
        }
        return deduped;
    }

    private SpecialtySummary requireActiveSpecialty(Long specialtyId) {
        if (specialtyId == null) {
            throw new ResourceNotFoundException("Especialidad no encontrada.");
        }
        SpecialtySummary specialty = professionalDirectory.findSpecialty(specialtyId)
                .orElseThrow(() -> new ResourceNotFoundException("Especialidad no encontrada."));
        if (!specialty.active()) {
            throw new ResourceNotFoundException("Especialidad inactiva.");
        }
        return specialty;
    }

    private void requireActiveProfessionalWithSpecialty(UUID professionalId, Long specialtyId) {
        ProfessionalSummary p = professionalDirectory.findById(professionalId)
                .orElseThrow(() -> new ResourceNotFoundException("Profesional no encontrado."));
        if (!p.active()) {
            throw new ResourceNotFoundException("Profesional inactivo.");
        }
        if (!professionalDirectory.hasActiveSpecialty(professionalId, specialtyId)) {
            throw new ResourceNotFoundException("El profesional no tiene la especialidad activa.");
        }
    }
}
