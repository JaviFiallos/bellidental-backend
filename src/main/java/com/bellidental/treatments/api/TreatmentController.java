package com.bellidental.treatments.api;

import com.bellidental.shared.response.ApiResponse;
import com.bellidental.treatments.api.dto.CreateTreatmentRequest;
import com.bellidental.treatments.api.dto.PerformedTreatmentResponse;
import com.bellidental.treatments.api.dto.RecordTreatmentRequest;
import com.bellidental.treatments.api.dto.TreatmentResponse;
import com.bellidental.treatments.api.dto.UpdateTreatmentRequest;
import com.bellidental.treatments.api.dto.VoidTreatmentRequest;
import com.bellidental.treatments.application.TreatmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TreatmentController {

    private final TreatmentService treatmentService;

    @GetMapping("/treatments")
    @PreAuthorize("hasAuthority('RECORD_READ')")
    public ResponseEntity<ApiResponse<List<TreatmentResponse>>> searchCatalog(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long specialtyId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(treatmentService.searchCatalog(query, specialtyId)));
    }

    @GetMapping("/treatments/manage")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<TreatmentResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok(treatmentService.listAll()));
    }

    @PostMapping("/treatments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TreatmentResponse>> create(@Valid @RequestBody CreateTreatmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tratamiento creado.", treatmentService.create(request)));
    }

    @PutMapping("/treatments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TreatmentResponse>> update(@PathVariable Long id, @Valid @RequestBody UpdateTreatmentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Tratamiento actualizado.", treatmentService.update(id, request)));
    }

    @PostMapping("/treatments/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TreatmentResponse>> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Tratamiento desactivado.", treatmentService.setActive(id, false)));
    }

    @PostMapping("/treatments/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TreatmentResponse>> activate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Tratamiento activado.", treatmentService.setActive(id, true)));
    }

    @PostMapping("/encounters/{id}/treatments")
    @PreAuthorize("hasAuthority('RECORD_CREATE')")
    public ResponseEntity<ApiResponse<PerformedTreatmentResponse>> recordTreatment(@PathVariable UUID id, @Valid @RequestBody RecordTreatmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(treatmentService.recordTreatment(id, request)));
    }

    @GetMapping("/encounters/{id}/treatments")
    @PreAuthorize("hasAuthority('RECORD_READ')")
    public ResponseEntity<ApiResponse<List<PerformedTreatmentResponse>>> getEncounterTreatments(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(treatmentService.getEncounterTreatments(id)));
    }

    @PostMapping("/performed-treatments/{id}/void")
    @PreAuthorize("hasAuthority('RECORD_UPDATE')")
    public ResponseEntity<ApiResponse<Void>> voidTreatment(@PathVariable UUID id, @Valid @RequestBody VoidTreatmentRequest request) {
        treatmentService.voidTreatment(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Tratamiento anulado."));
    }
}
