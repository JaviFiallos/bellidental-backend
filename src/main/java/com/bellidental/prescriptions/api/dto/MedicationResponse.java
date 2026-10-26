package com.bellidental.prescriptions.api.dto;

import java.util.List;

public record MedicationResponse(
        Long id,
        String name,
        String presentation,
        String concentration,
        String route,
        List<MedicationPresetResponse> presets,
        boolean active
) {}
