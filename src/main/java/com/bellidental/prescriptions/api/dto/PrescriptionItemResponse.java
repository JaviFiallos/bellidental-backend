package com.bellidental.prescriptions.api.dto;

import java.util.UUID;

public record PrescriptionItemResponse(
        UUID id,
        Long medicationId,
        String medicationName,
        String presentation,
        String concentration,
        Long medicationPresetId,
        String dose,
        String frequency,
        String duration,
        String quantity,
        String instructions
) {}
