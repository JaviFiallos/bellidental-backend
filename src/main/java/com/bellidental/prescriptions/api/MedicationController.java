package com.bellidental.prescriptions.api;

import com.bellidental.prescriptions.api.dto.MedicationPresetRequest;
import com.bellidental.prescriptions.api.dto.MedicationPresetResponse;
import com.bellidental.prescriptions.api.dto.MedicationRequest;
import com.bellidental.prescriptions.api.dto.MedicationResponse;
import com.bellidental.prescriptions.application.MedicationCatalogService;
import com.bellidental.prescriptions.application.MedicationService;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MedicationController {

    private final MedicationService medicationService;
    private final MedicationCatalogService medicationCatalogService;

    @GetMapping("/medications")
    @PreAuthorize("hasAuthority('RECORD_READ')")
    public ResponseEntity<ApiResponse<List<MedicationResponse>>> searchMedications(@RequestParam(required = false) String query) {
        return ResponseEntity.ok(ApiResponse.ok(medicationService.searchMedications(query)));
    }

    @GetMapping("/medications/manage")
    @PreAuthorize("hasAuthority('RECORD_CREATE')")
    public ResponseEntity<ApiResponse<List<MedicationResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok(medicationCatalogService.listAll()));
    }

    @PostMapping("/medications")
    @PreAuthorize("hasAuthority('RECORD_CREATE')")
    public ResponseEntity<ApiResponse<MedicationResponse>> create(@Valid @RequestBody MedicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Medicamento creado.", medicationCatalogService.create(request)));
    }

    @PutMapping("/medications/{id}")
    @PreAuthorize("hasAuthority('RECORD_CREATE')")
    public ResponseEntity<ApiResponse<MedicationResponse>> update(@PathVariable Long id, @Valid @RequestBody MedicationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Medicamento actualizado.", medicationCatalogService.update(id, request)));
    }

    @PostMapping("/medications/{id}/deactivate")
    @PreAuthorize("hasAuthority('RECORD_CREATE')")
    public ResponseEntity<ApiResponse<MedicationResponse>> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Medicamento desactivado.", medicationCatalogService.setActive(id, false)));
    }

    @PostMapping("/medications/{id}/activate")
    @PreAuthorize("hasAuthority('RECORD_CREATE')")
    public ResponseEntity<ApiResponse<MedicationResponse>> activate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Medicamento activado.", medicationCatalogService.setActive(id, true)));
    }

    @PostMapping("/medications/{id}/presets")
    @PreAuthorize("hasAuthority('RECORD_CREATE')")
    public ResponseEntity<ApiResponse<MedicationPresetResponse>> createPreset(@PathVariable Long id, @Valid @RequestBody MedicationPresetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Posología creada.", medicationCatalogService.createPreset(id, request)));
    }

    @PutMapping("/medication-presets/{id}")
    @PreAuthorize("hasAuthority('RECORD_CREATE')")
    public ResponseEntity<ApiResponse<MedicationPresetResponse>> updatePreset(@PathVariable Long id, @Valid @RequestBody MedicationPresetRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Posología actualizada.", medicationCatalogService.updatePreset(id, request)));
    }

    @PostMapping("/medication-presets/{id}/deactivate")
    @PreAuthorize("hasAuthority('RECORD_CREATE')")
    public ResponseEntity<ApiResponse<MedicationPresetResponse>> deactivatePreset(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Posología desactivada.", medicationCatalogService.setPresetActive(id, false)));
    }

    @PostMapping("/medication-presets/{id}/activate")
    @PreAuthorize("hasAuthority('RECORD_CREATE')")
    public ResponseEntity<ApiResponse<MedicationPresetResponse>> activatePreset(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Posología activada.", medicationCatalogService.setPresetActive(id, true)));
    }
}
