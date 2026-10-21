package com.bellidental.odontogram.api;

import com.bellidental.odontogram.api.dto.CatalogResponse;
import com.bellidental.odontogram.api.dto.CreateFindingRequest;
import com.bellidental.odontogram.api.dto.FindingResponse;
import com.bellidental.odontogram.api.dto.OdontogramResponse;
import com.bellidental.odontogram.application.OdontogramService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import com.bellidental.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OdontogramController {

    private final OdontogramService odontogramService;

    @GetMapping("/odontogram/catalog")
    @PreAuthorize("hasAuthority('RECORD_READ')")
    public ResponseEntity<ApiResponse<CatalogResponse>> getCatalog() {
        return ResponseEntity.ok(ApiResponse.ok(odontogramService.getCatalog()));
    }

    @PostMapping("/encounters/{id}/findings")
    @PreAuthorize("hasAuthority('RECORD_CREATE')")
    public ResponseEntity<ApiResponse<FindingResponse>> createFinding(@PathVariable UUID id, @Valid @RequestBody CreateFindingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(odontogramService.createFinding(id, request)));
    }

    @GetMapping("/encounters/{id}/findings")
    @PreAuthorize("hasAuthority('RECORD_READ')")
    public ResponseEntity<ApiResponse<List<FindingResponse>>> getEncounterFindings(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(odontogramService.getEncounterFindings(id)));
    }

    @PostMapping("/findings/{id}/void")
    @PreAuthorize("hasAuthority('RECORD_UPDATE')")
    public ResponseEntity<ApiResponse<Void>> voidFinding(@PathVariable UUID id) {
        odontogramService.voidFinding(id);
        return ResponseEntity.ok(ApiResponse.ok("Hallazgo anulado."));
    }

    @GetMapping("/patients/{id}/odontogram")
    @PreAuthorize("hasAuthority('RECORD_READ')")
    public ResponseEntity<ApiResponse<OdontogramResponse>> getPatientOdontogram(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(odontogramService.getPatientOdontogram(id)));
    }
}
