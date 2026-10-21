package com.bellidental.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
        @NotBlank String temporaryPassword
) {
    @Override
    public String toString() {
        return "ResetPasswordRequest[redacted]";
    }
}
