package com.bellidental.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Único dato que el ADMIN cambia de una cuenta existente: el email. */
public record UpdateUserRequest(
        @NotBlank @Email(message = "El email no tiene un formato válido")
        @Size(max = 150, message = "El email no debe superar los 150 caracteres")
        String email
) {}
