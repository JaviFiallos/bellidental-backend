package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.MedicationPreset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MedicationPresetRepository extends JpaRepository<MedicationPreset, Long> {

    List<MedicationPreset> findByMedicationIdInOrderByIdAsc(Collection<Long> medicationIds);
}
