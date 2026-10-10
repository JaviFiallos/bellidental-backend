package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.EncounterDiagnosis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EncounterDiagnosisRepository extends JpaRepository<EncounterDiagnosis, UUID> {

    List<EncounterDiagnosis> findByEncounterId(UUID encounterId);

    boolean existsByEncounterIdAndIcd10IdAndVoidedAtIsNull(UUID encounterId, Long icd10Id);

    long countByEncounterIdAndVoidedAtIsNull(UUID encounterId);
}
