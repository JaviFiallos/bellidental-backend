package com.bellidental.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Permiso granular sobre un módulo del sistema.
 * Ejemplo: code = "PATIENT_CREATE", module = "PACIENTES".
 */
@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "permission_id", updatable = false, nullable = false)
    private Long id;

    /** Identificador único del permiso. Ej: PATIENT_CREATE, BILLING_VIEW */
    @Column(nullable = false, unique = true, length = 80)
    private String code;

    /** Módulo al que pertenece. Ej: PACIENTES, CITAS, HISTORIA_CLINICA */
    @Column(nullable = false, length = 50)
    private String module;

    @Column(length = 255)
    private String description;
}
