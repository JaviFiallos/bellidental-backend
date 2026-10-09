package com.bellidental.professionals;

import com.bellidental.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * CRUD REST de profesionales clínicos y administrativos.
 */
@RestController
@RequestMapping("/api/v1/professionals")
@RequiredArgsConstructor
@Tag(name = "Profesionales", description = "Gestión de personal clínico y administrativo")
@SecurityRequirement(name = "bearerAuth")
public class ProfessionalController {

    private final ProfessionalService professionalService;

    @GetMapping
    @PreAuthorize("hasAuthority('PROFESSIONAL_READ')")
    @Operation(summary = "Listar todos los profesionales activos")
    public ResponseEntity<ApiResponse<List<ProfessionalResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.ok(professionalService.findAll()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PROFESSIONAL_READ')")
    @Operation(summary = "Obtener profesional por ID")
    public ResponseEntity<ApiResponse<ProfessionalResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(professionalService.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PROFESSIONAL_MANAGE')")
    @Operation(summary = "Registrar nuevo profesional")
    public ResponseEntity<ApiResponse<ProfessionalResponse>> create(
            @Valid @RequestBody CreateProfessionalRequest request
    ) {
        ProfessionalResponse response = professionalService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Profesional registrado exitosamente.", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PROFESSIONAL_MANAGE')")
    @Operation(summary = "Actualizar datos de un profesional")
    public ResponseEntity<ApiResponse<ProfessionalResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProfessionalRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Profesional actualizado.", professionalService.update(id, request)));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('PROFESSIONAL_MANAGE')")
    @Operation(summary = "Desactivar un profesional")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable UUID id) {
        professionalService.changeActiveStatus(id, false);
        return ResponseEntity.ok(ApiResponse.ok("Profesional desactivado."));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('PROFESSIONAL_MANAGE')")
    @Operation(summary = "Activar un profesional")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable UUID id) {
        professionalService.changeActiveStatus(id, true);
        return ResponseEntity.ok(ApiResponse.ok("Profesional activado."));
    }
}
