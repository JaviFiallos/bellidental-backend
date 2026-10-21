package com.bellidental.persistence.entity;

import com.bellidental.shared.enums.HistoryScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "medical_history_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "medical_history_entry_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinical_record_id", nullable = false)
    private ClinicalRecord clinicalRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medical_history_condition_id", nullable = false)
    private MedicalHistoryCondition condition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private HistoryScope scope;

    @Column(length = 30)
    private String relative;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private OffsetDateTime recordedAt;

    @Column(name = "voided_at")
    private OffsetDateTime voidedAt;
}
