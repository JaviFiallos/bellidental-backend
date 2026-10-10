package com.bellidental.clinical.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record UpdateEncounterRequest(
        @NotBlank String chiefComplaint,
        String currentIllness,
        @Min(50) @Max(260) Short bpSystolic,
        @Min(30) @Max(160) Short bpDiastolic,
        @Min(30) @Max(220) Short heartRate,
        @DecimalMin("34.0") @DecimalMax("43.0") BigDecimal temperature,
        @Min(5) @Max(60) Short respiratoryRate,
        String stomatognathicExam,
        @Pattern(regexp = "LEVE|MODERADA|SEVERA") String periodontalDisease,
        @Pattern(regexp = "LEVE|MODERADA|SEVERA") String fluorosis,
        @Pattern(regexp = "ANGLE_I|ANGLE_II|ANGLE_III") String malocclusion,
        String diagnosticPlan,
        String therapeuticPlan,
        String educationalPlan
) {}
