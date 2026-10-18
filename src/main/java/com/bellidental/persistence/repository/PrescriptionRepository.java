package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {

    List<Prescription> findByEncounterIdAndVoidedAtIsNullOrderByCreatedAtAsc(UUID encounterId);
}
