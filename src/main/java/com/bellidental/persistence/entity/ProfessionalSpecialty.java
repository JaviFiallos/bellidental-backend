package com.bellidental.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "professional_specialties")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfessionalSpecialty {

    @EmbeddedId
    private ProfessionalSpecialtyId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("professionalId")
    @JoinColumn(name = "professional_id", nullable = false)
    private Professional professional;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("specialtyId")
    @JoinColumn(name = "specialty_id", nullable = false)
    private Specialty specialty;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
