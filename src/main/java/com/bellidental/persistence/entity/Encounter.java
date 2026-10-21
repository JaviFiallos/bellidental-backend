package com.bellidental.persistence.entity;

import com.bellidental.shared.enums.EncounterStatus;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "encounters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Encounter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "encounter_id")
    private UUID id;

    @Column(name = "appointment_id", nullable = false, unique = true)
    private UUID appointmentId;

    @Column(name = "professional_id", nullable = false)
    private UUID professionalId;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at")
    private OffsetDateTime endAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "chief_complaint", nullable = false)
    private String chiefComplaint;

    @Column(name = "current_illness")
    private String currentIllness;

    @Column(name = "bp_systolic")
    private Short bpSystolic;

    @Column(name = "bp_diastolic")
    private Short bpDiastolic;

    @Column(name = "heart_rate")
    private Short heartRate;

    @Column(name = "temperature", precision = 4, scale = 1)
    private BigDecimal temperature;

    @Column(name = "respiratory_rate")
    private Short respiratoryRate;

    @Column(name = "stomatognathic_exam")
    private String stomatognathicExam;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "stomatognathic_regions", nullable = false, columnDefinition = "smallint[]")
    @Builder.Default
    private Short[] stomatognathicRegions = new Short[0];

    @Column(name = "periodontal_disease", length = 10)
    private String periodontalDisease;

    @Column(name = "malocclusion", length = 10)
    private String malocclusion;

    @Column(name = "fluorosis", length = 10)
    private String fluorosis;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "diagnostic_exams", nullable = false, columnDefinition = "varchar(20)[]")
    @Builder.Default
    private String[] diagnosticExams = new String[0];

    @Column(name = "plan_description")
    private String planDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    @Builder.Default
    private EncounterStatus status = EncounterStatus.OPEN;

    @Column(name = "voided_at")
    private OffsetDateTime voidedAt;

    @Column(name = "void_reason")
    private String voidReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
