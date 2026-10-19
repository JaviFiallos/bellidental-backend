package com.bellidental.prescriptions;

public record PrescriptionItemSummary(
        String medicationName,
        String concentration,
        String presentation,
        String dose,
        String frequency,
        String duration,
        String quantity
) {}
