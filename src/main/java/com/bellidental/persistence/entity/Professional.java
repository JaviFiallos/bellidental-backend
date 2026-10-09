package com.bellidental.persistence.entity;

import com.bellidental.shared.enums.DocumentType;
import com.bellidental.shared.enums.Specialty;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Profesional clínico o administrativo de la clínica Bellidental.
 * Puede estar vinculado opcionalmente a una cuenta {@link User} (1:1).
 */
@Entity
@Table(name = "professionals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Professional {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "professional_id", updatable = false, nullable = false)
    private UUID id;

    /** Cuenta de sistema asociada — opcional (puede operar sin portal). */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 20)
    private DocumentType documentType;

    @Column(name = "document_number", nullable = false, unique = true, length = 30)
    private String documentNumber;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /** Número de registro profesional / sanitario. */
    @Column(name = "license_number", nullable = false, unique = true, length = 50)
    private String licenseNumber;

    @Column(name = "phone_e164", length = 15)
    private String phoneE164;

    @Column(name = "google_calendar_id", length = 255)
    private String googleCalendarId;

    /** Color hexadecimal para identificar las citas del profesional en el calendario. */
    @Column(name = "color_tag", length = 7)
    private String colorTag;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
        this.active = false;
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
