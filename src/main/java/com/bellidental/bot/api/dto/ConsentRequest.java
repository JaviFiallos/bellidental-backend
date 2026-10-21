package com.bellidental.bot.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record ConsentRequest(
        @NotBlank(message = "El teléfono es obligatorio")
        String phone,
        UUID patientId
) {}
