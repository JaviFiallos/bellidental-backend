package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.MedicalHistoryEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MedicalHistoryEntryRepository extends JpaRepository<MedicalHistoryEntry, UUID> {
    
    List<MedicalHistoryEntry> findByClinicalRecordIdAndActiveTrueOrderByConditionDisplayOrderAsc(UUID clinicalRecordId);
    
    List<MedicalHistoryEntry> findByClinicalRecordIdOrderByConditionDisplayOrderAsc(UUID clinicalRecordId);
    
    boolean existsByClinicalRecordIdAndActiveTrue(UUID clinicalRecordId);
}
