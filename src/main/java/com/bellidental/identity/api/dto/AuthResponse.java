package com.bellidental.identity.api.dto;

/**
 * Respuesta de autenticación exitosa — contiene el token JWT y datos básicos del usuario.
 */
public record AuthResponse(
        String token,
        String username,
        String email
) {}
