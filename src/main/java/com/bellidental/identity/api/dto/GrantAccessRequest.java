package com.bellidental.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GrantAccessRequest(
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank String temporaryPassword
) {
    @Override
    public String toString() {
        return "GrantAccessRequest[redacted]";
    }
}
