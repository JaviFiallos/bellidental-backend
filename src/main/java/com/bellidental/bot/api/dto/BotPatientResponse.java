package com.bellidental.bot.api.dto;

import java.util.UUID;

public record BotPatientResponse(
        UUID patientId,
        String displayName
) {}
