package com.bellidental.bot.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BotSlotResponse(
        UUID professionalId,
        String professionalName,
        OffsetDateTime startAt,
        OffsetDateTime endAt
) {}
