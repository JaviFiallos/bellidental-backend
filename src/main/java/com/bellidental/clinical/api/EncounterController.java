package com.bellidental.clinical.api;

import com.bellidental.clinical.api.dto.AddDiagnosisRequest;
import com.bellidental.clinical.api.dto.DiagnosisResponse;
import com.bellidental.clinical.api.dto.EncounterResponse;
import com.bellidental.clinical.api.dto.OpenEncounterRequest;
import com.bellidental.clinical.api.dto.UpdateEncounterRequest;
import com.bellidental.clinical.api.dto.VoidEncounterRequest;
import com.bellidental.clinical.application.EncounterService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class EncounterController {

    private final EncounterService encounterService;

    @PostMapping("/api/v1/appointments/{appointmentId}/encounter")
    @PreAuthorize("hasAuthority('RECORD_CREATE')")
    public ResponseEntity<ApiResponse<EncounterResponse>> openEncounter(
            @PathVariable UUID appointmentId,
            @Valid @RequestBody OpenEncounterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Atención abierta.", encounterService.open(appointmentId, request)));
    }

    @PutMapping("/api/v1/encounters/{id}")
    @PreAuthorize("hasAuthority('RECORD_UPDATE')")
    public ResponseEntity<ApiResponse<EncounterResponse>> updateEncounter(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEncounterRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Atención actualizada.", encounterService.update(id, request)));
    }

    @PostMapping("/api/v1/encounters/{id}/diagnoses")
    @PreAuthorize("hasAuthority('RECORD_UPDATE')")
    public ResponseEntity<ApiResponse<DiagnosisResponse>> addDiagnosis(
            @PathVariable UUID id,
            @Valid @RequestBody AddDiagnosisRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Diagnóstico añadido.", encounterService.addDiagnosis(id, request)));
    }

    @PostMapping("/api/v1/encounter-diagnoses/{id}/void")
    @PreAuthorize("hasAuthority('RECORD_UPDATE')")
    public ResponseEntity<ApiResponse<Void>> voidDiagnosis(@PathVariable UUID id) {
        encounterService.voidDiagnosis(id);
        return ResponseEntity.ok(ApiResponse.ok("Diagnóstico anulado."));
    }

    @PostMapping("/api/v1/encounters/{id}/complete")
    @PreAuthorize("hasAuthority('RECORD_UPDATE')")
    public ResponseEntity<ApiResponse<EncounterResponse>> completeEncounter(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Atención completada.", encounterService.complete(id)));
    }

    @PostMapping("/api/v1/encounters/{id}/void")
    @PreAuthorize("hasAuthority('RECORD_UPDATE')")
    public ResponseEntity<ApiResponse<EncounterResponse>> voidEncounter(
            @PathVariable UUID id,
            @Valid @RequestBody VoidEncounterRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Atención anulada.", encounterService.voidEncounter(id, request)));
    }

    @GetMapping("/api/v1/encounters/{id}")
    @PreAuthorize("hasAuthority('RECORD_READ')")
    public ResponseEntity<ApiResponse<EncounterResponse>> getEncounter(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Atención encontrada.", encounterService.findById(id)));
    }
}
