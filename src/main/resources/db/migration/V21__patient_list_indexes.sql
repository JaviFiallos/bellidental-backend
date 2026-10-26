-- Índices para la consulta de lista de pacientes (tarea 029: últimos atendidos/registrados y saldo)

CREATE INDEX idx_encounters_active_start ON encounters (appointment_id, start_at DESC) WHERE voided_at IS NULL;

CREATE INDEX idx_payments_patient_active ON payments (patient_id) WHERE voided_at IS NULL;

CREATE INDEX idx_patients_created_at_desc ON patients (created_at DESC);

CREATE INDEX idx_performed_treatments_encounter_active ON performed_treatments (encounter_id) WHERE voided_at IS NULL;
