package com.bellidental.appointments.api;

import com.bellidental.appointments.api.dto.AppointmentResponse;
import com.bellidental.appointments.api.dto.CreateAppointmentRequest;
import com.bellidental.appointments.api.dto.RescheduleAppointmentRequest;
import com.bellidental.appointments.api.dto.WalkInRequest;
import com.bellidental.appointments.application.AppointmentService;
import com.bellidental.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping
    @PreAuthorize("hasAuthority('APPOINTMENT_CREATE')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> createAppointment(@Valid @RequestBody CreateAppointmentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Cita creada.", appointmentService.create(req)));
    }

    @PostMapping("/walk-in")
    @PreAuthorize("hasAuthority('APPOINTMENT_CREATE')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> createWalkIn(@Valid @RequestBody WalkInRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Atención sin cita registrada.", appointmentService.walkIn(req)));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('APPOINTMENT_UPDATE')")
    public ResponseEntity<ApiResponse<Void>> confirmAppointment(@PathVariable UUID id) {
        appointmentService.confirm(id);
        return ResponseEntity.ok(ApiResponse.ok("Cita confirmada."));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('APPOINTMENT_CANCEL')")
    public ResponseEntity<ApiResponse<Void>> cancelAppointment(@PathVariable UUID id) {
        appointmentService.cancel(id);
        return ResponseEntity.ok(ApiResponse.ok("Cita cancelada."));
    }

    @PostMapping("/{id}/check-in")
    @PreAuthorize("hasAuthority('APPOINTMENT_UPDATE')")
    public ResponseEntity<ApiResponse<Void>> checkInAppointment(@PathVariable UUID id) {
        appointmentService.checkIn(id);
        return ResponseEntity.ok(ApiResponse.ok("Check-in realizado."));
    }

    @PostMapping("/{id}/no-show")
    @PreAuthorize("hasAuthority('APPOINTMENT_UPDATE')")
    public ResponseEntity<ApiResponse<Void>> noShowAppointment(@PathVariable UUID id) {
        appointmentService.noShow(id);
        return ResponseEntity.ok(ApiResponse.ok("Marcada como no-show."));
    }

    @PostMapping("/{id}/reschedule")
    @PreAuthorize("hasAuthority('APPOINTMENT_UPDATE')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> rescheduleAppointment(@PathVariable UUID id, @Valid @RequestBody RescheduleAppointmentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Cita reprogramada.", appointmentService.reschedule(id, req)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('APPOINTMENT_READ')")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getAppointments(
            @RequestParam(required = false) UUID professionalId,
            @RequestParam OffsetDateTime from,
            @RequestParam OffsetDateTime to) {
        return ResponseEntity.ok(ApiResponse.ok(appointmentService.getAppointments(professionalId, from, to)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('APPOINTMENT_READ')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> getAppointment(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(appointmentService.getById(id)));
    }
}
