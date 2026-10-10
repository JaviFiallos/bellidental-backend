package com.bellidental.clinical.api.dto;

import com.bellidental.shared.enums.DiagnosisCertainty;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DiagnosisResponse(
        UUID id,
        Long icd10Id,
        String code,
        String description,
        DiagnosisCertainty certainty,
        String notes,
        OffsetDateTime createdAt,
        OffsetDateTime voidedAt
) {}
