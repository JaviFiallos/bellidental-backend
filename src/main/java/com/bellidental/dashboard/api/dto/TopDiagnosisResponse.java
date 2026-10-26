package com.bellidental.dashboard.api.dto;

public record TopDiagnosisResponse(
        String code,
        String description,
        long occurrences,
        long patients
) {}
