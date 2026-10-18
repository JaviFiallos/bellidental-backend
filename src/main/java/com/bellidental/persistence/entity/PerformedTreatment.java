package com.bellidental.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "performed_treatments")
@Getter
@Setter
@NoArgsConstructor
public class PerformedTreatment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "performed_treatment_id")
    private UUID id;

    @Column(name = "encounter_id", nullable = false)
    private UUID encounterId;

    @Column(name = "treatment_id", nullable = false)
    private Long treatmentId;

    @Column(name = "encounter_diagnosis_id")
    private UUID encounterDiagnosisId;

    @Column(name = "dental_finding_id")
    private UUID dentalFindingId;

    @Column(name = "tooth_code")
    private Short toothCode;

    @Column(name = "surface_code", columnDefinition = "bpchar")
    private String surfaceCode;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "voided_at")
    private OffsetDateTime voidedAt;

    @Column(name = "void_reason")
    private String voidReason;
}
