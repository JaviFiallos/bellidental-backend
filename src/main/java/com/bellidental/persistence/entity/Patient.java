package com.bellidental.persistence.entity;

import com.bellidental.shared.enums.BloodType;
import com.bellidental.shared.enums.DocumentType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Paciente de la clínica Bellidental.
 * Puede estar vinculado opcionalmente a una cuenta {@link User} para acceso al portal.
 */
@Entity
@Table(name = "patients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "patient_id", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 20)
    private DocumentType documentType;

    @Column(name = "document_number", nullable = false, unique = true, length = 30)
    private String documentNumber;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "sex", columnDefinition = "bpchar", nullable = false)
    private String sex;

    /** Tipo de sangre — relevante en procedimientos quirúrgicos. */
    @Enumerated(EnumType.STRING)
    @Column(name = "blood_type", length = 3)
    private BloodType bloodType;

    @Column(name = "phone_e164", length = 15)
    private String phoneE164;

    @Column(length = 150)
    private String email;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @Column(name = "emergency_contact_name", length = 150)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone_e164", length = 15)
    private String emergencyContactPhoneE164;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public void softDelete() {
        this.deletedAt = OffsetDateTime.now();
        this.active = false;
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
