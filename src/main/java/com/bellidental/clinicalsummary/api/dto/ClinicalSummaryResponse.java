package com.bellidental.clinicalsummary.api.dto;

import com.bellidental.shared.enums.DiagnosisCertainty;
import com.bellidental.shared.enums.EncounterStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ClinicalSummaryResponse(
        UUID patientId,
        String clinicName,
        String recordNumber,
        LocalDate openedAt,
        SessionProfessional openedBy,
        List<Session> sessions
) {

    public record SessionProfessional(
            UUID id,
            String fullName,
            String licenseNumber
    ) {}

    public record Session(
            UUID encounterId,
            int sessionNumber,
            OffsetDateTime startAt,
            EncounterStatus status,
            SessionProfessional professional,
            List<DiagnosisItem> diagnoses,
            List<TreatmentItem> treatments,
            List<PrescriptionItem> prescriptions
    ) {}

    public record DiagnosisItem(
            UUID id,
            String code,
            String description,
            DiagnosisCertainty certainty,
            String notes
    ) {}

    public record TreatmentItem(
            UUID id,
            String code,
            String name,
            Short toothCode,
            String surfaceCode,
            Integer quantity
    ) {}

    public record PrescriptionItem(
            UUID id,
            OffsetDateTime prescribedAt,
            String generalInstructions,
            List<PrescriptionMedicationItem> items
    ) {}

    public record PrescriptionMedicationItem(
            String medicationName,
            String concentration,
            String presentation,
            String dose,
            String frequency,
            String duration,
            String quantity
    ) {}
}
