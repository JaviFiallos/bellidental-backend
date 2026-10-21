package com.bellidental.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Datos actualizables de un usuario existente.
 * Todos los campos son opcionales — solo se actualiza lo que se envía.
 */
public record UpdateUserRequest(

        @Email(message = "El email no tiene un formato válido")
        @Size(max = 150, message = "El email no debe superar los 150 caracteres")
        String email,

        @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        String password,

        Boolean active
) {}
