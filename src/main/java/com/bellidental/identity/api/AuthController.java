package com.bellidental.identity.api;

import com.bellidental.identity.api.dto.AuthResponse;
import com.bellidental.identity.api.dto.LoginRequest;
import com.bellidental.identity.api.dto.MeResponse;
import com.bellidental.identity.application.AuthService;
import com.bellidental.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints públicos de autenticación — login.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticación", description = "Inicio de sesión y sesión actual")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión", description = "Valida credenciales y devuelve el token JWT.")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Inicio de sesión exitoso.", response));
    }

    @GetMapping("/me")
    @Operation(summary = "Obtener datos del usuario actual", description = "Devuelve los datos del usuario autenticado a partir del token JWT.")
    public ResponseEntity<ApiResponse<MeResponse>> me() {
        MeResponse response = authService.me();
        return ResponseEntity.ok(ApiResponse.ok("Datos del usuario.", response));
    }
}
