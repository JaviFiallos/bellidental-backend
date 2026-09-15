package com.bellidental.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Petición de registro de un nuevo usuario del sistema.
 */
public record RegisterUserRequest(

        @NotBlank(message = "El username es obligatorio")
        @Size(min = 3, max = 60, message = "El username debe tener entre 3 y 60 caracteres")
        String username,

        @NotBlank(message = "El email es obligatorio")
        @Email(message = "El email no tiene un formato válido")
        @Size(max = 150, message = "El email no debe superar los 150 caracteres")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        String password,

        /** Código del rol a asignar (ADMIN, ODONTOLOGO, SECRETARIA). Opcional — default: SECRETARIA */
        String roleCode
) {}
