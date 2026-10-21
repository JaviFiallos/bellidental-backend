package com.bellidental.persistence.entity;

import com.bellidental.shared.enums.ReminderResponse;
import com.bellidental.shared.enums.ReminderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "appointment_reminders")
@Getter
@Setter
@NoArgsConstructor
public class AppointmentReminder {

    public static final String KIND_DAY_BEFORE = "DAY_BEFORE";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "reminder_id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;

    @Column(name = "kind", nullable = false, length = 10)
    private String kind = KIND_DAY_BEFORE;

    @Column(name = "scheduled_at", nullable = false)
    private OffsetDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ReminderStatus status = ReminderStatus.PENDING;

    @Column(name = "wa_message_id", length = 100, unique = true)
    private String waMessageId;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "response", length = 11)
    private ReminderResponse response;

    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;

    public AppointmentReminder(UUID appointmentId, String kind, OffsetDateTime scheduledAt, ReminderStatus status) {
        this.appointmentId = appointmentId;
        this.kind = kind;
        this.scheduledAt = scheduledAt;
        this.status = status;
    }
}
