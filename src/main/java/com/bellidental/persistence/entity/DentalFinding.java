package com.bellidental.persistence.entity;

import org.hibernate.envers.Audited;
import com.bellidental.shared.enums.FindingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Audited
@Entity
@Table(name = "dental_findings")
public class DentalFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "dental_finding_id")
    private UUID dentalFindingId;

    @Column(name = "encounter_id", nullable = false)
    private UUID encounterId;

    @Column(name = "tooth_code", nullable = false)
    private Short toothCode;

    @Column(name = "end_tooth_code")
    private Short endToothCode;

    @Column(name = "surface_code", columnDefinition = "bpchar")
    private String surfaceCode;

    @Column(name = "dental_condition_id", nullable = false)
    private Long dentalConditionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private FindingStatus status;

    @Column
    private Short grade;

    @Column
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "voided_at")
    private OffsetDateTime voidedAt;

    public UUID getDentalFindingId() {
        return dentalFindingId;
    }

    public void setDentalFindingId(UUID dentalFindingId) {
        this.dentalFindingId = dentalFindingId;
    }

    public UUID getEncounterId() {
        return encounterId;
    }

    public void setEncounterId(UUID encounterId) {
        this.encounterId = encounterId;
    }

    public Short getToothCode() {
        return toothCode;
    }

    public void setToothCode(Short toothCode) {
        this.toothCode = toothCode;
    }

    public Short getEndToothCode() {
        return endToothCode;
    }

    public void setEndToothCode(Short endToothCode) {
        this.endToothCode = endToothCode;
    }

    public String getSurfaceCode() {
        return surfaceCode;
    }

    public void setSurfaceCode(String surfaceCode) {
        this.surfaceCode = surfaceCode;
    }

    public Long getDentalConditionId() {
        return dentalConditionId;
    }

    public void setDentalConditionId(Long dentalConditionId) {
        this.dentalConditionId = dentalConditionId;
    }

    public FindingStatus getStatus() {
        return status;
    }

    public void setStatus(FindingStatus status) {
        this.status = status;
    }

    public Short getGrade() {
        return grade;
    }

    public void setGrade(Short grade) {
        this.grade = grade;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getVoidedAt() {
        return voidedAt;
    }

    public void setVoidedAt(OffsetDateTime voidedAt) {
        this.voidedAt = voidedAt;
    }
}
