package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.PerformedTreatment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface PerformedTreatmentRepository extends JpaRepository<PerformedTreatment, UUID> {

    List<PerformedTreatment> findByEncounterIdAndVoidedAtIsNullOrderByCreatedAtAsc(UUID encounterId);

    List<PerformedTreatment> findByEncounterIdInAndVoidedAtIsNullOrderByCreatedAtAsc(Collection<UUID> encounterIds);
}
