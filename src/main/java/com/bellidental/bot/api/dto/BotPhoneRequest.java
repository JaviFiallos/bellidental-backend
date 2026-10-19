package com.bellidental.bot.api.dto;

import jakarta.validation.constraints.NotBlank;

public record BotPhoneRequest(
        @NotBlank(message = "El teléfono es obligatorio.")
        String phone
) {}
