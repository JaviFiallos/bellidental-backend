package com.bellidental.bot.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ReminderSentRequest(
        @NotBlank(message = "El ID del mensaje de WhatsApp es obligatorio.")
        String waMessageId
) {}
