-- V9__treatments.sql
CREATE TABLE treatment_catalog (
    treatment_id BIGSERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    base_price DECIMAL(10,2) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_treatment_base_price CHECK (base_price >= 0)
);

CREATE INDEX idx_treatment_catalog_name_trgm ON treatment_catalog USING gin (name gin_trgm_ops);

CREATE TABLE performed_treatments (
    performed_treatment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id UUID NOT NULL REFERENCES encounters(encounter_id),
    treatment_id BIGINT NOT NULL REFERENCES treatment_catalog(treatment_id),
    encounter_diagnosis_id UUID REFERENCES encounter_diagnoses(encounter_diagnosis_id),
    dental_finding_id UUID REFERENCES dental_findings(dental_finding_id),
    tooth_code SMALLINT REFERENCES teeth(tooth_code),
    surface_code CHAR(1) REFERENCES tooth_surfaces(surface_code),
    quantity INT NOT NULL DEFAULT 1,
    price DECIMAL(10,2) NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    voided_at TIMESTAMPTZ,
    void_reason TEXT,
    CONSTRAINT chk_performed_treatment_quantity CHECK (quantity >= 1),
    CONSTRAINT chk_performed_treatment_price CHECK (price >= 0)
);

CREATE INDEX idx_performed_treatments_encounter_id ON performed_treatments(encounter_id);

-- Semilla del catálogo. Precios provisionales en USD; la clínica dará los reales.
INSERT INTO treatment_catalog (code, name, base_price) VALUES
('D0150', 'Examen clínico integral', 20.00),
('D1110', 'Profilaxis (limpieza dental)', 25.00),
('D1330', 'Instrucción de higiene oral', 10.00),
('D1351', 'Sellante de fosas y fisuras (por diente)', 15.00),
('D2140', 'Obturación con amalgama, una superficie', 30.00),
('D2330', 'Resina compuesta, una superficie anterior', 35.00),
('D2391', 'Resina compuesta, una superficie posterior', 45.00),
('D2940', 'Recubrimiento protector (curación)', 25.00),
('D3310', 'Endodoncia de diente anterior', 120.00),
('D3320', 'Endodoncia de premolar', 150.00),
('D3330', 'Endodoncia de molar', 180.00),
('D7140', 'Exodoncia simple', 30.00);
