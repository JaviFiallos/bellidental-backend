package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.DentalFinding;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DentalFindingRepository extends JpaRepository<DentalFinding, UUID> {
    
    List<DentalFinding> findByEncounterIdAndVoidedAtIsNull(UUID encounterId);
    
    @Query("""
        SELECT df FROM DentalFinding df
        JOIN Encounter e ON df.encounterId = e.id
        JOIN Appointment a ON e.appointmentId = a.id
        WHERE a.patientId = :patientId
          AND df.voidedAt IS NULL
          AND e.voidedAt IS NULL
        ORDER BY df.createdAt ASC
    """)
    List<DentalFinding> findValidFindingsByPatientIdOrderByCreatedAt(@Param("patientId") UUID patientId);
}
