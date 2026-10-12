package com.bellidental.clinicalrecords;

import com.bellidental.security.CurrentUser;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ClinicalRecordController {

    private final ClinicalRecordService clinicalRecordService;
    private final CurrentUser currentUser;

    @PostMapping("/patients/{patientId}/clinical-record")
    @PreAuthorize("hasAuthority('RECORD_CREATE')")
    public ResponseEntity<ApiResponse<ClinicalRecordResponse>> openClinicalRecord(@PathVariable UUID patientId) {
        UUID professionalId = currentUser.professionalId().orElse(null);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Historia clínica abierta.", clinicalRecordService.open(patientId, professionalId)));
    }

    @GetMapping("/patients/{patientId}/clinical-record")
    @PreAuthorize("hasAuthority('RECORD_READ')")
    public ResponseEntity<ApiResponse<ClinicalRecordResponse>> getClinicalRecord(
            @PathVariable UUID patientId,
            @RequestParam(defaultValue = "false") boolean includeVoided) {
        return ResponseEntity.ok(ApiResponse.ok(clinicalRecordService.getByPatientId(patientId, includeVoided)));
    }

    @PutMapping("/clinical-records/{id}")
    @PreAuthorize("hasAuthority('RECORD_UPDATE')")
    public ResponseEntity<ApiResponse<ClinicalRecordResponse>> updateClinicalRecord(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClinicalRecordRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Historia clínica actualizada.", clinicalRecordService.update(id, request)));
    }

    @GetMapping("/medical-history-conditions")
    @PreAuthorize("hasAuthority('RECORD_READ')")
    public ResponseEntity<ApiResponse<List<MedicalHistoryConditionResponse>>> getMedicalHistoryConditions() {
        return ResponseEntity.ok(ApiResponse.ok(clinicalRecordService.getActiveConditions()));
    }

    @PostMapping("/clinical-records/{id}/history-entries")
    @PreAuthorize("hasAuthority('RECORD_UPDATE')")
    public ResponseEntity<ApiResponse<MedicalHistoryEntryResponse>> addHistoryEntry(
            @PathVariable UUID id,
            @Valid @RequestBody CreateMedicalHistoryEntryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Antecedente registrado.", clinicalRecordService.addEntry(id, request)));
    }

    @PostMapping("/history-entries/{id}/void")
    @PreAuthorize("hasAuthority('RECORD_UPDATE')")
    public ResponseEntity<ApiResponse<Void>> voidHistoryEntry(@PathVariable UUID id) {
        clinicalRecordService.voidEntry(id);
        return ResponseEntity.ok(ApiResponse.ok("Antecedente anulado."));
    }
}
