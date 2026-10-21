package com.bellidental.clinical.api.dto;

import com.bellidental.shared.enums.DiagnosisCertainty;
import jakarta.validation.constraints.NotNull;

public record AddDiagnosisRequest(
        @NotNull Long icd10Id,
        @NotNull DiagnosisCertainty certainty,
        String notes
) {}
