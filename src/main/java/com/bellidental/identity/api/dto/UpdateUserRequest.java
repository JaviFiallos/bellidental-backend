package com.bellidental.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Datos que el ADMIN cambia de una cuenta existente: email y/o nombre de usuario. */
public record UpdateUserRequest(
        @Email(message = "El email no tiene un formato válido")
        @Size(max = 150, message = "El email no debe superar los 150 caracteres")
        String email,

        @Size(max = 60, message = "El nombre de usuario no debe superar los 60 caracteres")
        String username
) {}
