package com.bellidental.prescriptions.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record PrescriptionRequest(
        @NotNull UUID encounterDiagnosisId,
        String generalInstructions,
        @Valid @NotEmpty List<PrescriptionItemRequest> items
) {}
