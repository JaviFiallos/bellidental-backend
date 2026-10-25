package com.bellidental.patients;

import com.bellidental.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * CRUD REST de pacientes de la clínica Bellidental.
 */
@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
@Tag(name = "Pacientes", description = "Gestión de pacientes de la clínica")
@SecurityRequirement(name = "bearerAuth")
public class PatientController {

    private final PatientService patientService;
    private final PatientListService patientListService;

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('PATIENT_READ')")
    @Operation(summary = "Lista informativa y paginada de pacientes (últimos atendidos o registrados)")
    public ResponseEntity<ApiResponse<Page<PatientListItem>>> list(
            @RequestParam(required = false) String view,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false, defaultValue = "false") boolean withBalance,
            @RequestParam(required = false) String query,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok(patientListService.listPatients(view, from, to, withBalance, query, page, size)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PATIENT_READ')")
    @Operation(summary = "Buscar pacientes paginados por texto (nombre/apellido) o dígitos (cédula/teléfono)")
    public ResponseEntity<ApiResponse<Page<PatientResponse>>> search(
            @RequestParam(required = false, defaultValue = "") String query,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size
    ) {
        if (size > 50) size = 50;
        return ResponseEntity.ok(ApiResponse.ok(patientService.search(query, page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PATIENT_READ')")
    @Operation(summary = "Obtener paciente por ID")
    public ResponseEntity<ApiResponse<PatientResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(patientService.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PATIENT_CREATE')")
    @Operation(summary = "Registrar nuevo paciente")
    public ResponseEntity<ApiResponse<PatientResponse>> create(
            @Valid @RequestBody CreatePatientRequest request
    ) {
        PatientResponse response = patientService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Paciente registrado exitosamente.", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PATIENT_UPDATE')")
    @Operation(summary = "Actualizar datos de un paciente")
    public ResponseEntity<ApiResponse<PatientResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePatientRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Paciente actualizado.", patientService.update(id, request)));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('PATIENT_UPDATE')")
    @Operation(summary = "Desactivar un paciente")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable UUID id) {
        patientService.changeActiveStatus(id, false);
        return ResponseEntity.ok(ApiResponse.ok("Paciente desactivado."));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('PATIENT_UPDATE')")
    @Operation(summary = "Activar un paciente")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable UUID id) {
        patientService.changeActiveStatus(id, true);
        return ResponseEntity.ok(ApiResponse.ok("Paciente activado."));
    }
}
