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
import org.hibernate.envers.Audited;

@Audited
@Entity
@Table(name = "medication_presets")
@Getter
@Setter
@NoArgsConstructor
public class MedicationPreset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "medication_preset_id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "medication_id", nullable = false)
    private Long medicationId;

    @Column(nullable = false, length = 60)
    private String label;

    @Column(nullable = false, length = 100)
    private String dose;

    @Column(nullable = false, length = 100)
    private String frequency;

    @Column(nullable = false, length = 100)
    private String duration;

    @Column
    private String instructions;

    @Column(nullable = false)
    private boolean active = true;
}
