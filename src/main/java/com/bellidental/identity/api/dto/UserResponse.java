package com.bellidental.identity.api.dto;

import com.bellidental.persistence.entity.User;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Proyección de un usuario para respuestas — excluye datos sensibles (passwordHash).
 */
public record UserResponse(
        UUID id,
        String username,
        String email,
        boolean active,
        OffsetDateTime lastLoginAt,
        OffsetDateTime createdAt,
        List<String> roles
) {
    /** Fábrica estática para mapear desde la entidad. */
    public static UserResponse from(User user) {
        List<String> roles = user.getUserRoles().stream()
                .map(ur -> ur.getRole().getCode())
                .toList();

        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.isActive(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                roles
        );
    }
}
