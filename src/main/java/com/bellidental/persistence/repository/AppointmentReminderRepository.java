package com.bellidental.persistence.repository;

import com.bellidental.persistence.entity.AppointmentReminder;
import com.bellidental.shared.enums.ReminderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppointmentReminderRepository extends JpaRepository<AppointmentReminder, UUID> {

    List<AppointmentReminder> findByAppointmentIdAndStatus(UUID appointmentId, ReminderStatus status);

    boolean existsByAppointmentIdAndKindAndStatus(UUID appointmentId, String kind, ReminderStatus status);

    Optional<AppointmentReminder> findByWaMessageId(String waMessageId);

    List<AppointmentReminder> findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            ReminderStatus status,
            OffsetDateTime scheduledAt
    );
}
