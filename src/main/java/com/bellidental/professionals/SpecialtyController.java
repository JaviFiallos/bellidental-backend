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

@RestController
@RequestMapping("/api/v1/specialties")
@RequiredArgsConstructor
@Tag(name = "Especialidades", description = "Gestión de especialidades clínicas")
@SecurityRequirement(name = "bearerAuth")
public class SpecialtyController {

    private final SpecialtyService specialtyService;

    @GetMapping
    @PreAuthorize("hasAuthority('PROFESSIONAL_READ')")
    @Operation(summary = "Listar todas las especialidades activas")
    public ResponseEntity<ApiResponse<List<SpecialtyResponse>>> findAllActive() {
        return ResponseEntity.ok(ApiResponse.ok(specialtyService.findAllActive()));
    }

    @GetMapping("/manage")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar todas las especialidades, activas e inactivas")
    public ResponseEntity<ApiResponse<List<SpecialtyResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.ok(specialtyService.findAll()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear una especialidad")
    public ResponseEntity<ApiResponse<SpecialtyResponse>> create(@Valid @RequestBody CreateSpecialtyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Especialidad creada.", specialtyService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Editar nombre o duración de una especialidad")
    public ResponseEntity<ApiResponse<SpecialtyResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSpecialtyRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Especialidad actualizada.", specialtyService.update(id, request)));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desactivar una especialidad si no tiene profesionales ni citas futuras")
    public ResponseEntity<ApiResponse<SpecialtyResponse>> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Especialidad desactivada.", specialtyService.deactivate(id)));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reactivar una especialidad")
    public ResponseEntity<ApiResponse<SpecialtyResponse>> activate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Especialidad activada.", specialtyService.activate(id)));
    }
}
