package com.bellidental.identity.api;

import com.bellidental.identity.api.dto.GrantAccessRequest;
import com.bellidental.identity.api.dto.UserResponse;
import com.bellidental.identity.application.ProfessionalAccessService;
import com.bellidental.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/professionals")
@RequiredArgsConstructor
@Tag(name = "Profesionales", description = "Cuentas de acceso de profesionales")
@SecurityRequirement(name = "bearerAuth")
public class ProfessionalAccessController {

    private final ProfessionalAccessService professionalAccessService;

    @PostMapping("/{id}/access")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> grantAccess(@PathVariable UUID id, @Valid @RequestBody GrantAccessRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Cuenta de acceso creada.", professionalAccessService.grantAccess(id, request)));
    }
}
