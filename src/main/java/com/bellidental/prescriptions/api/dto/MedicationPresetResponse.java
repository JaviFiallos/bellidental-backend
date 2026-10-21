package com.bellidental.prescriptions.api.dto;

public record MedicationPresetResponse(
        Long id,
        String label,
        String dose,
        String frequency,
        String duration,
        String instructions
) {}
