package com.bellidental.professionals;

import com.bellidental.persistence.repository.SpecialtyRepository;
import com.bellidental.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/specialties")
@RequiredArgsConstructor
@Tag(name = "Especialidades", description = "Gestión de especialidades clínicas")
@SecurityRequirement(name = "bearerAuth")
public class SpecialtyController {

    private final SpecialtyRepository specialtyRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('PROFESSIONAL_READ')")
    @Operation(summary = "Listar todas las especialidades activas")
    public ResponseEntity<ApiResponse<List<SpecialtyResponse>>> findAllActive() {
        List<SpecialtyResponse> list = specialtyRepository.findByActiveTrueOrderByName()
                .stream()
                .map(SpecialtyResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(list));
    }
}
