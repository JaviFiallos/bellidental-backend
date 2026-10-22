package com.bellidental.professionals;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSpecialtyRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull Integer defaultDurationMinutes
) {}
