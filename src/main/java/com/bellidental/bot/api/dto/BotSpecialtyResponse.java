package com.bellidental.bot.api.dto;

public record BotSpecialtyResponse(
        Long specialtyId,
        String name,
        Integer durationMinutes
) {}
