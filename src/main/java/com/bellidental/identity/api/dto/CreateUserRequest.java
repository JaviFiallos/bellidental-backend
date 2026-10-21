package com.bellidental.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateUserRequest(
        @NotBlank @Email @Size(max = 150) String email,
        @Size(max = 60) String username,
        @NotBlank String temporaryPassword,
        @NotEmpty Set<String> roles
) {
    @Override
    public String toString() {
        return "CreateUserRequest[redacted]";
    }
}
