package com.bellidental.persistence.entity;

import org.hibernate.envers.Audited;
import com.bellidental.shared.enums.PrescriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Audited
@Entity
@Table(name = "prescriptions")
@Getter
@Setter
@NoArgsConstructor
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "prescription_id")
    private UUID id;

    @Column(name = "encounter_id", nullable = false)
    private UUID encounterId;

    @Column(name = "encounter_diagnosis_id")
    private UUID encounterDiagnosisId;

    @Column(name = "prescribed_at", nullable = false)
    private OffsetDateTime prescribedAt;

    @Column(name = "general_instructions")
    private String generalInstructions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PrescriptionStatus status;

    @Column(name = "voided_at")
    private OffsetDateTime voidedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
