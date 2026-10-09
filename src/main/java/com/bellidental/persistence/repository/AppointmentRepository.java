package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.Appointment;
import com.bellidental.shared.enums.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
    
    List<Appointment> findByStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(OffsetDateTime from, OffsetDateTime to);
    
    List<Appointment> findByProfessionalIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(UUID professionalId, OffsetDateTime from, OffsetDateTime to);

    @Query("SELECT a FROM Appointment a WHERE a.professionalId = :professionalId AND a.status IN :activeStatuses " +
            "AND a.startAt < :to AND a.endAt > :from ORDER BY a.startAt ASC")
    List<Appointment> findOverlappingWithStatuses(@Param("professionalId") UUID professionalId,
                                                  @Param("from") OffsetDateTime from,
                                                  @Param("to") OffsetDateTime to,
                                                  @Param("activeStatuses") Collection<AppointmentStatus> activeStatuses);

    /** Citas que ocupan agenda: PENDING, CONFIRMED y ATTENDED (las demás no bloquean el EXCLUDE). */
    default List<Appointment> findActiveOverlapping(UUID professionalId, OffsetDateTime from, OffsetDateTime to) {
        return findOverlappingWithStatuses(professionalId, from, to,
                EnumSet.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED, AppointmentStatus.ATTENDED));
    }
}
