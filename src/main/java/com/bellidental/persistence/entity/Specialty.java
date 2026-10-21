package com.bellidental.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

@Audited
@Entity
@Table(name = "specialties")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Specialty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "specialty_id", updatable = false, nullable = false)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "default_duration_minutes", nullable = false)
    @Builder.Default
    private int defaultDurationMinutes = 30;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
