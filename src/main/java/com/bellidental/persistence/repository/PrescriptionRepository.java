package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.Prescription;
import com.bellidental.shared.enums.PrescriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {

    List<Prescription> findByEncounterIdAndVoidedAtIsNullOrderByCreatedAtAsc(UUID encounterId);

    List<Prescription> findByEncounterIdInAndStatusAndVoidedAtIsNullOrderByPrescribedAtAsc(
            Collection<UUID> encounterIds,
            PrescriptionStatus status
    );
}
