package com.bellidental.identity.api;

import com.bellidental.identity.api.dto.CreateUserRequest;
import com.bellidental.identity.api.dto.ResetPasswordRequest;
import com.bellidental.identity.api.dto.UpdateUserRequest;
import com.bellidental.identity.api.dto.UpdateUserRolesRequest;
import com.bellidental.identity.api.dto.UserResponse;
import com.bellidental.identity.application.UserService;
import com.bellidental.security.CurrentUser;
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
 * Gestión de cuentas por el ADMIN (D-24): alta, roles, contraseña temporal y desactivación.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "Gestión de usuarios del sistema")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;
    private final CurrentUser currentUser;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar todos los usuarios activos")
    public ResponseEntity<ApiResponse<List<UserResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.ok(userService.findAll()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtener usuario por ID")
    public ResponseEntity<ApiResponse<UserResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear usuario (ODONTOLOGO o SECRETARIA) con contraseña temporal")
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Usuario creado.", userService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cambiar el email de un usuario")
    public ResponseEntity<ApiResponse<UserResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Usuario actualizado.", userService.update(id, request)));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reemplazar los roles de un usuario (ODONTOLOGO y SECRETARIA)")
    public ResponseEntity<ApiResponse<UserResponse>> updateRoles(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRolesRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Roles actualizados.", userService.updateRoles(id, request)));
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Restablecer la contraseña con una contraseña temporal")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable UUID id,
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        userService.resetPassword(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Contraseña restablecida."));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desactivar un usuario (no puede ser el propio ADMIN)")
    public ResponseEntity<ApiResponse<UserResponse>> deactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Usuario desactivado.", userService.deactivate(id, currentUser.userId())));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reactivar un usuario")
    public ResponseEntity<ApiResponse<UserResponse>> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("Usuario activado.", userService.activate(id)));
    }
}
