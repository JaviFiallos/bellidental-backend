package com.bellidental.bot.api.dto;

import java.util.UUID;

public record ReminderReplyResponse(
        String outcome,
        UUID appointmentId,
        UUID patientId
) {}
