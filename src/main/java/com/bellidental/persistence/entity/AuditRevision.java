package com.bellidental.persistence.entity;

import com.bellidental.security.AuditRevisionListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "revision_info")
@RevisionEntity(AuditRevisionListener.class)
@Getter
@Setter
@NoArgsConstructor
public class AuditRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @RevisionNumber
    @Column(name = "revision_id")
    private Long id;

    @RevisionTimestamp
    @Column(name = "revision_at", nullable = false)
    private Instant at;

    @Column(name = "user_id")
    private UUID userId;
}
