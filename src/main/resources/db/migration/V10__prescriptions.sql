-- V10__prescriptions.sql
CREATE TABLE medications (
    medication_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    presentation VARCHAR(100),
    concentration VARCHAR(100),
    route VARCHAR(50),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE UNIQUE INDEX uq_medications_name_concentration_presentation ON medications(name, concentration, presentation);
CREATE INDEX idx_medications_name_trgm ON medications USING gin (name gin_trgm_ops);

CREATE TABLE medication_presets (
    medication_preset_id BIGSERIAL PRIMARY KEY,
    medication_id BIGINT NOT NULL REFERENCES medications(medication_id),
    label VARCHAR(60) NOT NULL,
    dose VARCHAR(100) NOT NULL,
    frequency VARCHAR(100) NOT NULL,
    duration VARCHAR(100) NOT NULL,
    instructions TEXT
);

CREATE TABLE prescriptions (
    prescription_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id UUID NOT NULL REFERENCES encounters(encounter_id),
    encounter_diagnosis_id UUID REFERENCES encounter_diagnoses(encounter_diagnosis_id),
    prescribed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    general_instructions TEXT,
    status VARCHAR(10) NOT NULL DEFAULT 'DRAFT',
    voided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_prescriptions_status CHECK (status IN ('DRAFT', 'ISSUED'))
);

CREATE INDEX idx_prescriptions_encounter_id ON prescriptions(encounter_id);

CREATE TABLE prescription_items (
    prescription_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prescription_id UUID NOT NULL REFERENCES prescriptions(prescription_id),
    medication_id BIGINT NOT NULL REFERENCES medications(medication_id),
    medication_preset_id BIGINT REFERENCES medication_presets(medication_preset_id),
    dose VARCHAR(100) NOT NULL,
    frequency VARCHAR(100) NOT NULL,
    duration VARCHAR(100) NOT NULL,
    quantity VARCHAR(50),
    instructions TEXT
);

-- Semilla de medicamentos de uso odontológico con sus posologías predefinidas (presets).
INSERT INTO medications (name, presentation, concentration, route) VALUES
('Amoxicilina', 'Cápsula', '500 mg', 'Oral'),
('Ibuprofeno', 'Tableta', '400 mg', 'Oral'),
('Paracetamol', 'Tableta', '500 mg', 'Oral'),
('Clindamicina', 'Cápsula', '300 mg', 'Oral'),
('Ketorolaco', 'Tableta', '10 mg', 'Oral'),
('Clorhexidina', 'Enjuague bucal', '0,12 %', 'Bucal'),
('Metronidazol', 'Tableta', '500 mg', 'Oral'),
('Lidocaína con epinefrina', 'Solución inyectable', '2 % / 1:100.000', 'Infiltrativa');

INSERT INTO medication_presets (medication_id, label, dose, frequency, duration, instructions) VALUES
((SELECT medication_id FROM medications WHERE name = 'Amoxicilina'), 'Cada 8 h × 7 días', '1 cápsula', 'cada 8 horas', '7 días', 'Tomar con alimentos.'),
((SELECT medication_id FROM medications WHERE name = 'Amoxicilina'), 'Cada 12 h × 5 días', '1 cápsula', 'cada 12 horas', '5 días', 'Tomar con alimentos.'),
((SELECT medication_id FROM medications WHERE name = 'Ibuprofeno'), 'Cada 8 h × 3 días', '1 tableta', 'cada 8 horas', '3 días', 'Tomar después de los alimentos.'),
((SELECT medication_id FROM medications WHERE name = 'Paracetamol'), 'Cada 6 h × 3 días', '1 tableta', 'cada 6 horas', '3 días', NULL),
((SELECT medication_id FROM medications WHERE name = 'Clindamicina'), 'Cada 8 h × 7 días', '1 cápsula', 'cada 8 horas', '7 días', 'Tomar con un vaso de agua completo.'),
((SELECT medication_id FROM medications WHERE name = 'Ketorolaco'), 'Cada 8 h × 3 días', '1 tableta', 'cada 8 horas', '3 días', 'Tomar después de los alimentos. No usar más de 5 días.'),
((SELECT medication_id FROM medications WHERE name = 'Clorhexidina'), 'Dos veces al día × 14 días', '15 ml', 'cada 12 horas', '14 días', 'Enjuagar 30 segundos después del cepillado. No tragar.'),
((SELECT medication_id FROM medications WHERE name = 'Metronidazol'), 'Cada 8 h × 7 días', '1 tableta', 'cada 8 horas', '7 días', 'Evitar alcohol durante el tratamiento.'),
((SELECT medication_id FROM medications WHERE name = 'Lidocaína con epinefrina'), 'Anestesia infiltrativa', '1 cartucho (1,8 ml)', 'dosis única', 'sesión única', NULL);
