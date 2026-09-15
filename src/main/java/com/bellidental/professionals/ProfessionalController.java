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
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'ODONTOLOGO')")
    @Operation(summary = "Listar todos los profesionales activos")
    public ResponseEntity<ApiResponse<List<ProfessionalResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.ok(professionalService.findAll()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SECRETARIA', 'ODONTOLOGO')")
    @Operation(summary = "Obtener profesional por ID")
    public ResponseEntity<ApiResponse<ProfessionalResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(professionalService.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Registrar nuevo profesional")
    public ResponseEntity<ApiResponse<ProfessionalResponse>> create(
            @Valid @RequestBody CreateProfessionalRequest request
    ) {
        ProfessionalResponse response = professionalService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Profesional registrado exitosamente.", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar datos de un profesional")
    public ResponseEntity<ApiResponse<ProfessionalResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProfessionalRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Profesional actualizado.", professionalService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar profesional (soft delete)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        professionalService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Profesional eliminado exitosamente."));
    }
}
