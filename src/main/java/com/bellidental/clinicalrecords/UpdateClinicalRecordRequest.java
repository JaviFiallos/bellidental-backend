package com.bellidental.clinicalrecords;

public record UpdateClinicalRecordRequest(
        Boolean noKnownHistory,
        String currentMedication
) {}
