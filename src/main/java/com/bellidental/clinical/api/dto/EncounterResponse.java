package com.bellidental.clinical.api.dto;

import com.bellidental.shared.enums.EncounterStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record EncounterResponse(
        UUID id,
        UUID appointmentId,
        UUID patientId,
        UUID professionalId,
        EncounterStatus status,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        OffsetDateTime closedAt,
        String chiefComplaint,
        String currentIllness,
        Short bpSystolic,
        Short bpDiastolic,
        Short heartRate,
        BigDecimal temperature,
        Short respiratoryRate,
        String stomatognathicExam,
        String periodontalDisease,
        String fluorosis,
        String malocclusion,
        String diagnosticPlan,
        String therapeuticPlan,
        String educationalPlan,
        OffsetDateTime voidedAt,
        String voidReason,
        List<DiagnosisResponse> diagnoses
) {}
