package com.bellidental.appointments.api;

import com.bellidental.appointments.api.dto.EffectiveDayResponse;
import com.bellidental.appointments.api.dto.ScheduleRequest;
import com.bellidental.appointments.api.dto.ScheduleResponse;
import com.bellidental.appointments.application.ScheduleService;
import com.bellidental.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public ResponseEntity<ApiResponse<List<ScheduleResponse>>> getSchedules(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(scheduleService.getSchedules(id)));
    }

    @GetMapping("/professionals/{id}/schedules/effective")
    @PreAuthorize("hasAuthority('PROFESSIONAL_READ')")
    public ResponseEntity<ApiResponse<List<EffectiveDayResponse>>> getEffectiveSchedule(
            @PathVariable UUID id,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok(scheduleService.getEffectiveSchedule(id, from, to)));
    }

    @PostMapping("/professionals/{id}/schedules")
    @PreAuthorize("hasAuthority('SCHEDULE_MANAGE')")
    public ResponseEntity<ApiResponse<ScheduleResponse>> createSchedule(
            @PathVariable UUID id,
            @Valid @RequestBody ScheduleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Horario registrado.", scheduleService.create(id, req)));
    }

    @PutMapping("/schedules/{id}")
    @PreAuthorize("hasAuthority('SCHEDULE_MANAGE')")
    public ResponseEntity<ApiResponse<ScheduleResponse>> updateSchedule(
            @PathVariable Long id,
            @Valid @RequestBody ScheduleRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Horario actualizado.", scheduleService.update(id, req)));
    }

    @PostMapping("/schedules/{id}/deactivate")
    @PreAuthorize("hasAuthority('SCHEDULE_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deactivateSchedule(@PathVariable Long id) {
        scheduleService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.ok("Horario desactivado."));
    }
}
