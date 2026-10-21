package com.bellidental.clinicalsummary.api;

import com.bellidental.clinicalsummary.api.dto.ClinicalSummaryResponse;
import com.bellidental.clinicalsummary.application.ClinicalSummaryService;
import com.bellidental.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ClinicalSummaryController {

    private final ClinicalSummaryService clinicalSummaryService;

    @GetMapping("/patients/{id}/clinical-summary")
    @PreAuthorize("hasAuthority('RECORD_READ')")
    public ResponseEntity<ApiResponse<ClinicalSummaryResponse>> getClinicalSummary(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(clinicalSummaryService.getSummary(id)));
    }
}
