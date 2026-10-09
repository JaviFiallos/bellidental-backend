package com.bellidental.identity.api.dto;

import java.util.List;

/**
 * Respuesta de autenticación exitosa.
 */
public record AuthResponse(
        String accessToken,
        long expiresIn,
        List<String> roles
) {}
