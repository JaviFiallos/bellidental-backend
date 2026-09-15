package com.bellidental.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Credenciales de login.
 */
public record LoginRequest(

        @NotBlank(message = "El username es obligatorio")
        String username,

        @NotBlank(message = "La contraseña es obligatoria")
        String password
) {}
