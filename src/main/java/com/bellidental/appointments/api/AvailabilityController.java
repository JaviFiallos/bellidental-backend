package com.bellidental.appointments.api;

import com.bellidental.appointments.AvailableSlot;
import com.bellidental.appointments.application.AvailabilityService;
import com.bellidental.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/availability")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @GetMapping
    @PreAuthorize("hasAuthority('APPOINTMENT_READ')")
    public ResponseEntity<ApiResponse<List<AvailableSlot>>> getAvailability(
            @RequestParam UUID professionalId,
            @RequestParam Long specialtyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer durationMinutes) {
        return ResponseEntity.ok(ApiResponse.ok(
                availabilityService.findAvailableSlots(professionalId, specialtyId, date, durationMinutes)));
    }
}
