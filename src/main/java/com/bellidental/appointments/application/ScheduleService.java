package com.bellidental.appointments.application;

import com.bellidental.appointments.api.dto.EffectiveDayResponse;
import com.bellidental.appointments.api.dto.ScheduleRequest;
import com.bellidental.appointments.api.dto.ScheduleResponse;
import com.bellidental.appointments.domain.EffectiveScheduleResolver;
import com.bellidental.appointments.domain.ScheduleRule;
import com.bellidental.appointments.domain.Shift;
import com.bellidental.persistence.entity.ProfessionalSchedule;
import com.bellidental.persistence.repository.ProfessionalScheduleRepository;
import com.bellidental.professionals.ProfessionalDirectory;
import com.bellidental.shared.exception.BusinessException;
import com.bellidental.shared.exception.InvalidInputException;
import com.bellidental.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ProfessionalScheduleRepository scheduleRepository;
    private final ProfessionalDirectory professionalDirectory;

    private void validateProfessionalExists(UUID professionalId) {
        if (professionalDirectory.findById(professionalId).isEmpty()) {
            throw new ResourceNotFoundException("Profesional no encontrado.");
        }
    }

    private ScheduleResponse toResponse(ProfessionalSchedule schedule) {
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getWeekday(),
                schedule.getSpecificDate(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.isAvailable()
        );
    }

    static ScheduleRule toRule(ProfessionalSchedule schedule) {
        return new ScheduleRule(
                schedule.getWeekday(),
                schedule.getSpecificDate(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.isAvailable()
        );
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedules(UUID professionalId) {
        validateProfessionalExists(professionalId);
        return scheduleRepository.findByProfessionalIdAndActiveTrue(professionalId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EffectiveDayResponse> getEffectiveSchedule(UUID professionalId, LocalDate from, LocalDate to) {
        validateProfessionalExists(professionalId);
        
        if (to.isBefore(from)) {
            throw new InvalidInputException("La fecha 'to' no puede ser anterior a 'from'.");
        }
        if (ChronoUnit.DAYS.between(from, to) > 31) {
            throw new InvalidInputException("El rango no puede ser mayor a 31 días.");
        }

        List<ScheduleRule> activeSchedules = scheduleRepository.findByProfessionalIdAndActiveTrue(professionalId).stream()
                .map(ScheduleService::toRule)
                .toList();
        
        List<EffectiveDayResponse> responses = new ArrayList<>();
        LocalDate current = from;
        while (!current.isAfter(to)) {
            List<Shift> shifts = EffectiveScheduleResolver.resolve(current, activeSchedules);
            responses.add(new EffectiveDayResponse(current, shifts));
            current = current.plusDays(1);
        }
        
        return responses;
    }

    private void validateRequest(ScheduleRequest request) {
        boolean hasWeekday = request.weekday() != null;
        boolean hasSpecificDate = request.specificDate() != null;
        if (hasWeekday == hasSpecificDate) {
            throw new InvalidInputException("Debe indicar exactamente uno: weekday o specificDate.");
        }
        
        if (Boolean.TRUE.equals(request.available())) {
            if (request.startTime() == null || request.endTime() == null) {
                throw new InvalidInputException("Turno disponible requiere startTime y endTime.");
            }
            if (!request.endTime().isAfter(request.startTime())) {
                throw new InvalidInputException("endTime debe ser posterior a startTime.");
            }
        }
    }

    private void checkOverlap(UUID professionalId, ScheduleRequest request, Long excludeScheduleId) {
        if (!Boolean.TRUE.equals(request.available())) {
            return;
        }
        List<ProfessionalSchedule> activeSchedules = scheduleRepository.findByProfessionalIdAndActiveTrue(professionalId);
        
        boolean overlap = activeSchedules.stream()
                .filter(schedule -> !schedule.getId().equals(excludeScheduleId))
                .filter(ProfessionalSchedule::isAvailable)
                .filter(schedule -> {
                    if (request.weekday() != null) {
                        return request.weekday().equals(schedule.getWeekday());
                    } else {
                        return request.specificDate().equals(schedule.getSpecificDate());
                    }
                })
                .anyMatch(schedule -> {
                    LocalTime maxStart = request.startTime().isAfter(schedule.getStartTime()) ? request.startTime() : schedule.getStartTime();
                    LocalTime minEnd = request.endTime().isBefore(schedule.getEndTime()) ? request.endTime() : schedule.getEndTime();
                    return maxStart.isBefore(minEnd);
                });
        
        if (overlap) {
            throw new BusinessException("El turno se solapa con otro horario existente.");
        }
    }

    @Transactional
    public ScheduleResponse create(UUID professionalId, ScheduleRequest request) {
        validateProfessionalExists(professionalId);
        validateRequest(request);
        checkOverlap(professionalId, request, null);

        ProfessionalSchedule schedule = ProfessionalSchedule.builder()
                .professionalId(professionalId)
                .weekday(request.weekday())
                .specificDate(request.specificDate())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .available(request.available())
                .active(true)
                .build();
        
        return toResponse(scheduleRepository.save(schedule));
    }

    @Transactional
    public ScheduleResponse update(Long scheduleId, ScheduleRequest request) {
        ProfessionalSchedule schedule = scheduleRepository.findById(scheduleId)
                .filter(ProfessionalSchedule::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Horario no encontrado."));

        validateRequest(request);
        checkOverlap(schedule.getProfessionalId(), request, scheduleId);

        schedule.setWeekday(request.weekday());
        schedule.setSpecificDate(request.specificDate());
        schedule.setStartTime(request.startTime());
        schedule.setEndTime(request.endTime());
        schedule.setAvailable(request.available());

        return toResponse(scheduleRepository.save(schedule));
    }

    @Transactional
    public void deactivate(Long scheduleId) {
        ProfessionalSchedule schedule = scheduleRepository.findById(scheduleId)
                .filter(ProfessionalSchedule::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Horario no encontrado."));
        
        schedule.setActive(false);
        scheduleRepository.save(schedule);
    }
}
