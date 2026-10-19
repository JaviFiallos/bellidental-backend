package com.bellidental.bot.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DueReminderResponse(
        UUID reminderId,
        UUID appointmentId,
        String phone,
        String patientFirstName,
        String professionalName,
        OffsetDateTime startAt
) {}
