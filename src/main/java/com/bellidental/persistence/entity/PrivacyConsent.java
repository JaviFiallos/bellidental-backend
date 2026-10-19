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

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "privacy_consents")
@Getter
@Setter
@NoArgsConstructor
public class PrivacyConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "consent_id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "phone_e164", nullable = false, length = 15)
    private String phoneE164;

    @Column(name = "patient_id")
    private UUID patientId;

    @Column(name = "notice_version", nullable = false, length = 10)
    private String noticeVersion;

    @Column(name = "accepted_at", nullable = false)
    private OffsetDateTime acceptedAt;
}
