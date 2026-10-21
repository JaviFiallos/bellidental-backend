package com.bellidental.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ProfessionalSpecialtyId implements Serializable {

    @Column(name = "professional_id")
    private UUID professionalId;

    @Column(name = "specialty_id")
    private Long specialtyId;
}
