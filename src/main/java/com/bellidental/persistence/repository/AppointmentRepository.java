package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
    
    List<Appointment> findByStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(OffsetDateTime from, OffsetDateTime to);
    
    List<Appointment> findByProfessionalIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(UUID professionalId, OffsetDateTime from, OffsetDateTime to);
}
