package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.ClinicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClinicalRecordRepository extends JpaRepository<ClinicalRecord, UUID> {
    
    Optional<ClinicalRecord> findByPatientId(UUID patientId);

    @Query(value = "SELECT nextval('clinical_record_number_seq')", nativeQuery = true)
    Long getNextRecordNumber();
}
