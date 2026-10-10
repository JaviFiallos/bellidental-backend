-- V7_1 - encounters and encounter_diagnoses
CREATE TABLE encounters (
    encounter_id        uuid            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    appointment_id      uuid            NOT NULL UNIQUE REFERENCES appointments(appointment_id),
    professional_id     uuid            NOT NULL REFERENCES professionals(professional_id),
    start_at            timestamptz     NOT NULL,
    end_at              timestamptz,
    closed_at           timestamptz,
    chief_complaint     text            NOT NULL,
    current_illness     text,
    bp_systolic         smallint,
    bp_diastolic        smallint,
    heart_rate          smallint,
    temperature         decimal(4,1),
    respiratory_rate    smallint,
    stomatognathic_exam text,
    periodontal_disease varchar(10),
    malocclusion        varchar(10),
    fluorosis           varchar(10),
    diagnostic_plan     text,
    therapeutic_plan    text,
    educational_plan    text,
    status              varchar(10)     NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','COMPLETED')),
    voided_at           timestamptz,
    void_reason         text,
    created_at          timestamptz     NOT NULL DEFAULT now(),
    updated_at          timestamptz     NOT NULL DEFAULT now()
);

CREATE INDEX idx_encounters_professional_id ON encounters(professional_id);

CREATE TABLE encounter_diagnoses (
    encounter_diagnosis_id  uuid        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    encounter_id            uuid        NOT NULL REFERENCES encounters(encounter_id),
    icd10_id                bigint      NOT NULL REFERENCES icd10_codes(icd10_id),
    certainty               varchar(12) NOT NULL CHECK (certainty IN ('PRESUMPTIVE','DEFINITIVE')),
    notes                   text,
    created_at              timestamptz NOT NULL DEFAULT now(),
    voided_at               timestamptz
);

CREATE INDEX idx_encounter_diagnoses_encounter_id ON encounter_diagnoses(encounter_id);

CREATE UNIQUE INDEX idx_encounter_diagnoses_active
    ON encounter_diagnoses(encounter_id, icd10_id)
    WHERE voided_at IS NULL;
