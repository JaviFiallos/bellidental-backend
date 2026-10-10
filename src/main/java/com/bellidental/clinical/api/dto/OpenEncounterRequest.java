package com.bellidental.clinical.api.dto;

import jakarta.validation.constraints.NotBlank;

public record OpenEncounterRequest(
        @NotBlank String chiefComplaint
) {}
