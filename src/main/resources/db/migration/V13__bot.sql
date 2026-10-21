-- V13__bot.sql
CREATE TABLE appointment_reminders (
    reminder_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id UUID NOT NULL REFERENCES appointments(appointment_id),
    kind VARCHAR(10) NOT NULL,
    scheduled_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    wa_message_id VARCHAR(100) UNIQUE,
    sent_at TIMESTAMPTZ,
    response VARCHAR(11),
    responded_at TIMESTAMPTZ,
    CONSTRAINT chk_appointment_reminders_kind CHECK (kind IN ('DAY_BEFORE')),
    CONSTRAINT chk_appointment_reminders_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'RESPONDED', 'CANCELLED')),
    CONSTRAINT chk_appointment_reminders_response CHECK (response IS NULL OR response IN ('CONFIRMED', 'CANCELLED', 'RESCHEDULED'))
);

CREATE INDEX idx_appointment_reminders_status_scheduled ON appointment_reminders(status, scheduled_at);
CREATE INDEX idx_appointment_reminders_appointment_id ON appointment_reminders(appointment_id);
CREATE UNIQUE INDEX uq_appointment_reminders_pending ON appointment_reminders(appointment_id, kind) WHERE status = 'PENDING';

CREATE TABLE privacy_consents (
    consent_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_e164 VARCHAR(15) NOT NULL,
    patient_id UUID REFERENCES patients(patient_id),
    notice_version VARCHAR(10) NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_privacy_consents_phone_e164 ON privacy_consents(phone_e164);
