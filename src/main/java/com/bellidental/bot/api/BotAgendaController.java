package com.bellidental.bot.api;

import com.bellidental.bot.api.dto.BotAppointmentResponse;
import com.bellidental.bot.api.dto.BotBookRequest;
import com.bellidental.bot.api.dto.BotPhoneRequest;
import com.bellidental.bot.api.dto.BotProfessionalResponse;
import com.bellidental.bot.api.dto.BotRescheduleRequest;
import com.bellidental.bot.api.dto.BotSlotResponse;
import com.bellidental.bot.api.dto.BotSpecialtyResponse;
import com.bellidental.bot.api.dto.UrgentSlotsResponse;
import com.bellidental.bot.application.BotAppointmentQueryService;
import com.bellidental.bot.application.BotAvailabilityService;
import com.bellidental.bot.application.BotBookingService;
import com.bellidental.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bot")
@RequiredArgsConstructor
public class BotAgendaController {

    private final BotAvailabilityService botAvailabilityService;
    private final BotAppointmentQueryService botAppointmentQueryService;
    private final BotBookingService botBookingService;

    @GetMapping("/specialties")
    public ResponseEntity<ApiResponse<List<BotSpecialtyResponse>>> getSpecialties() {
        return ResponseEntity.ok(ApiResponse.ok(botAvailabilityService.getActiveSpecialties()));
    }

    @GetMapping("/professionals")
    public ResponseEntity<ApiResponse<List<BotProfessionalResponse>>> getProfessionals(
            @RequestParam Long specialtyId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(botAvailabilityService.getActiveProfessionalsBySpecialty(specialtyId)));
    }

    @GetMapping("/availability/days")
    public ResponseEntity<ApiResponse<List<LocalDate>>> getAvailableDays(
            @RequestParam Long specialtyId,
            @RequestParam(required = false) UUID professionalId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(botAvailabilityService.getAvailableDays(specialtyId, professionalId)));
    }

    @GetMapping("/availability")
    public ResponseEntity<ApiResponse<List<BotSlotResponse>>> getAvailableSlots(
            @RequestParam Long specialtyId,
            @RequestParam(required = false) UUID professionalId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(ApiResponse.ok(botAvailabilityService.getAvailableSlots(specialtyId, professionalId, date)));
    }

    @GetMapping("/urgent-slots")
    public ResponseEntity<ApiResponse<UrgentSlotsResponse>> getUrgentSlots() {
        return ResponseEntity.ok(ApiResponse.ok(botAvailabilityService.getUrgentSlots()));
    }

    @GetMapping("/appointments")
    public ResponseEntity<ApiResponse<List<BotAppointmentResponse>>> getAppointments(
            @RequestParam String phone,
            @RequestParam UUID patientId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(botAppointmentQueryService.getAppointmentsByPatientAndPhone(phone, patientId)));
    }

    @PostMapping("/appointments")
    public ResponseEntity<ApiResponse<BotAppointmentResponse>> book(
            @Valid @RequestBody BotBookRequest request
    ) {
        BotAppointmentResponse response = botBookingService.bookAppointment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Cita agendada.", response));
    }

    @PostMapping("/appointments/{id}/reschedule")
    public ResponseEntity<ApiResponse<BotAppointmentResponse>> reschedule(
            @PathVariable UUID id,
            @Valid @RequestBody BotRescheduleRequest request
    ) {
        BotAppointmentResponse response = botBookingService.rescheduleAppointment(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Cita reprogramada.", response));
    }

    @PostMapping("/appointments/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @PathVariable UUID id,
            @Valid @RequestBody BotPhoneRequest request
    ) {
        botBookingService.cancelAppointment(id, request.phone());
        return ResponseEntity.ok(ApiResponse.ok("Cita cancelada."));
    }

    @PostMapping("/appointments/{id}/confirm")
    public ResponseEntity<ApiResponse<Void>> confirm(
            @PathVariable UUID id,
            @Valid @RequestBody BotPhoneRequest request
    ) {
        botBookingService.confirmAppointment(id, request.phone());
        return ResponseEntity.ok(ApiResponse.ok("Cita confirmada."));
    }
}
