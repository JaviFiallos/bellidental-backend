package com.bellidental.clinicalrecords;

import com.bellidental.security.CurrentUser;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ClinicalRecordController {

    private final ClinicalRecordService clinicalRecordService;
    private final CurrentUser currentUser;

    @PostMapping("/patients/{patientId}/clinical-record")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RECORD_CREATE')")
    public ClinicalRecordResponse openClinicalRecord(@PathVariable UUID patientId) {
        UUID professionalId = currentUser.professionalId().orElse(null);
        return clinicalRecordService.open(patientId, professionalId);
    }

    @GetMapping("/patients/{patientId}/clinical-record")
    @PreAuthorize("hasAuthority('RECORD_READ')")
    public ClinicalRecordResponse getClinicalRecord(
            @PathVariable UUID patientId,
            @RequestParam(defaultValue = "false") boolean includeVoided) {
        return clinicalRecordService.getByPatientId(patientId, includeVoided);
    }

    @PutMapping("/clinical-records/{id}")
    @PreAuthorize("hasAuthority('RECORD_UPDATE')")
    public ClinicalRecordResponse updateClinicalRecord(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClinicalRecordRequest request) {
        return clinicalRecordService.update(id, request);
    }

    @GetMapping("/medical-history-conditions")
    @PreAuthorize("hasAuthority('RECORD_READ')")
    public List<MedicalHistoryConditionResponse> getMedicalHistoryConditions() {
        return clinicalRecordService.getActiveConditions();
    }

    @PostMapping("/clinical-records/{id}/history-entries")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RECORD_UPDATE')")
    public MedicalHistoryEntryResponse addHistoryEntry(
            @PathVariable UUID id,
            @Valid @RequestBody CreateMedicalHistoryEntryRequest request) {
        return clinicalRecordService.addEntry(id, request);
    }

    @PostMapping("/history-entries/{id}/void")
    @PreAuthorize("hasAuthority('RECORD_UPDATE')")
    public void voidHistoryEntry(@PathVariable UUID id) {
        clinicalRecordService.voidEntry(id);
    }
}
