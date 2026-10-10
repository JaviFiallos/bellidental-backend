package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.Encounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EncounterRepository extends JpaRepository<Encounter, UUID> {

    boolean existsByAppointmentId(UUID appointmentId);

    Optional<Encounter> findByAppointmentId(UUID appointmentId);
}
