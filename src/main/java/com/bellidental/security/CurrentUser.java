package com.bellidental.security;

import com.bellidental.shared.exception.BusinessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Permite obtener los datos del usuario autenticado en la petición actual.
 */
@Component
public class CurrentUser {

    public UUID userId() {
        return getPrincipal().userId();
    }

    public Optional<UUID> professionalId() {
        return getPrincipal().professionalId();
    }

    private CurrentUserPrincipal getPrincipal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUserPrincipal)) {
            throw new BusinessException("No hay usuario autenticado en el contexto de seguridad.");
        }
        return (CurrentUserPrincipal) authentication.getPrincipal();
    }

    public record CurrentUserPrincipal(UUID userId, Optional<UUID> professionalId) {}
}
