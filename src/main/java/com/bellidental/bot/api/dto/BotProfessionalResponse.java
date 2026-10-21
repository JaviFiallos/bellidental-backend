package com.bellidental.bot.api.dto;

import java.util.UUID;

public record BotProfessionalResponse(
        UUID professionalId,
        String displayName
) {}
