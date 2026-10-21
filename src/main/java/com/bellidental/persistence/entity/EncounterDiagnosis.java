package com.bellidental.persistence.entity;

import com.bellidental.shared.enums.DiagnosisCertainty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "encounter_diagnoses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EncounterDiagnosis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "encounter_diagnosis_id")
    private UUID id;

    @Column(name = "encounter_id", nullable = false)
    private UUID encounterId;

    @Column(name = "icd10_id", nullable = false)
    private Long icd10Id;

    @Enumerated(EnumType.STRING)
    @Column(name = "certainty", nullable = false, length = 12)
    private DiagnosisCertainty certainty;

    @Column(name = "notes")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "voided_at")
    private OffsetDateTime voidedAt;
}
