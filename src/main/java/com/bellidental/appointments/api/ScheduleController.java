package com.bellidental.appointments.api;

import com.bellidental.appointments.api.dto.EffectiveDayResponse;
import com.bellidental.appointments.api.dto.ScheduleRequest;
import com.bellidental.appointments.api.dto.ScheduleResponse;
import com.bellidental.appointments.application.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping("/professionals/{id}/schedules")
    @PreAuthorize("hasAuthority('PROFESSIONAL_READ')")
    public List<ScheduleResponse> getSchedules(@PathVariable UUID id) {
        return scheduleService.getSchedules(id);
    }

    @GetMapping("/professionals/{id}/schedules/effective")
    @PreAuthorize("hasAuthority('PROFESSIONAL_READ')")
    public List<EffectiveDayResponse> getEffectiveSchedule(
            @PathVariable UUID id,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return scheduleService.getEffectiveSchedule(id, from, to);
    }

    @PostMapping("/professionals/{id}/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SCHEDULE_MANAGE')")
    public ScheduleResponse createSchedule(@PathVariable UUID id, @Valid @RequestBody ScheduleRequest req) {
        return scheduleService.create(id, req);
    }

    @PutMapping("/schedules/{id}")
    @PreAuthorize("hasAuthority('SCHEDULE_MANAGE')")
    public ScheduleResponse updateSchedule(@PathVariable Long id, @Valid @RequestBody ScheduleRequest req) {
        return scheduleService.update(id, req);
    }

    @PostMapping("/schedules/{id}/deactivate")
    @PreAuthorize("hasAuthority('SCHEDULE_MANAGE')")
    public void deactivateSchedule(@PathVariable Long id) {
        scheduleService.deactivate(id);
    }
}
