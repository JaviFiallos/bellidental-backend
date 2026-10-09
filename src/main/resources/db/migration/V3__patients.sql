CREATE TABLE patients (
    patient_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    document_type varchar(20) NOT NULL,
    document_number varchar(30) NOT NULL,
    first_name varchar(100) NOT NULL,
    last_name varchar(100) NOT NULL,
    birth_date date NOT NULL,
    sex char(1) NOT NULL CHECK (sex IN ('M', 'F')),
    phone_e164 varchar(15),
    email varchar(150),
    blood_type varchar(3),
    emergency_contact_name varchar(150),
    emergency_contact_phone_e164 varchar(15),
    admin_notes text,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    UNIQUE (document_type, document_number)
);

CREATE INDEX idx_patients_phone_e164 ON patients(phone_e164);
