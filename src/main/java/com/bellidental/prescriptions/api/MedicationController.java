package com.bellidental.prescriptions.api;

import com.bellidental.prescriptions.api.dto.MedicationResponse;
import com.bellidental.prescriptions.application.MedicationService;
import com.bellidental.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MedicationController {

    private final MedicationService medicationService;

    @GetMapping("/medications")
    @PreAuthorize("hasAuthority('RECORD_READ')")
    public ResponseEntity<ApiResponse<List<MedicationResponse>>> searchMedications(@RequestParam(required = false) String query) {
        return ResponseEntity.ok(ApiResponse.ok(medicationService.searchMedications(query)));
    }
}
