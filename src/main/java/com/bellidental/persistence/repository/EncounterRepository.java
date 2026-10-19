package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.Encounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EncounterRepository extends JpaRepository<Encounter, UUID> {

    boolean existsByAppointmentId(UUID appointmentId);

    Optional<Encounter> findByAppointmentId(UUID appointmentId);

    List<Encounter> findByAppointmentIdIn(Collection<UUID> appointmentIds);

    @Query("""
        SELECT e FROM Encounter e
        JOIN Appointment a ON e.appointmentId = a.id
        WHERE a.patientId = :patientId
          AND e.voidedAt IS NULL
        ORDER BY e.startAt ASC
    """)
    List<Encounter> findActiveByPatientIdOrderByStartAtAsc(@Param("patientId") UUID patientId);
}
