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

@Entity
@Table(name = "icd10_codes")
@Getter
@Setter
@NoArgsConstructor
public class Icd10Code {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "icd10_id", updatable = false, nullable = false)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(length = 255)
    private String category;

    @Column(name = "included_terms", length = 500)
    private String includedTerms;

    @Column(nullable = false)
    private boolean active = true;
}
