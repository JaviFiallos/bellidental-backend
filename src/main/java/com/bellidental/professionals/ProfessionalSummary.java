package com.bellidental.professionals;

import java.util.UUID;

/**
 * Representación pública de un profesional para otros módulos.
 */
public record ProfessionalSummary(
        UUID id,
        UUID userId,
        String firstName,
        String lastName,
        String phoneE164,
        String googleCalendarId,
        String colorTag,
        boolean active
) {}
