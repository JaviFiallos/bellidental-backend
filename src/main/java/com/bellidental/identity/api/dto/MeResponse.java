package com.bellidental.identity.api.dto;

import java.util.List;
import java.util.UUID;

public record MeResponse(
        UUID userId,
        String email,
        List<String> roles,
        List<String> permissions,
        UUID professionalId
) {}
