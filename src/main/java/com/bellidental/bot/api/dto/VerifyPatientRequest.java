package com.bellidental.bot.api.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyPatientRequest(
        @NotBlank(message = "El teléfono es obligatorio")
        String phone,
        @NotBlank(message = "El número de documento es obligatorio")
        String idNumber
) {}
