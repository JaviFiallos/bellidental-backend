package com.bellidental.bot.api.dto;

import com.bellidental.shared.enums.ReminderResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ReminderReplyRequest(
        @NotBlank(message = "El ID del mensaje de WhatsApp es obligatorio.")
        String waMessageId,

        @NotBlank(message = "El teléfono es obligatorio.")
        String phone,

        @NotNull(message = "La respuesta es obligatoria.")
        ReminderResponse response
) {}
