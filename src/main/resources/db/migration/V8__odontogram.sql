-- V8__odontogram.sql
CREATE TABLE teeth (
    tooth_code SMALLINT PRIMARY KEY,
    dentition VARCHAR(10) NOT NULL,
    arch VARCHAR(5) NOT NULL,
    quadrant SMALLINT NOT NULL,
    position SMALLINT NOT NULL,
    tooth_type VARCHAR(10) NOT NULL
);

CREATE TABLE tooth_surfaces (
    surface_code CHAR(1) PRIMARY KEY,
    name VARCHAR(30) NOT NULL
);

CREATE TABLE dental_conditions (
    dental_condition_id BIGSERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(12) NOT NULL,
    scope VARCHAR(8) NOT NULL,
    grade_min SMALLINT,
    grade_max SMALLINT,
    cpo_component CHAR(1),
    ceo_component CHAR(1),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_condition_category CHECK (category IN ('DENTAL','PERIODONTAL')),
    CONSTRAINT chk_condition_scope CHECK (scope IN ('TOOTH','SURFACE','SPAN')),
    CONSTRAINT chk_condition_cpo CHECK (cpo_component IN ('C','P','O')),
    CONSTRAINT chk_condition_ceo CHECK (ceo_component IN ('c','e','o')),
    CONSTRAINT chk_condition_grade CHECK ((grade_min IS NULL AND grade_max IS NULL) OR (grade_min IS NOT NULL AND grade_max IS NOT NULL AND grade_min <= grade_max))
);

CREATE TABLE dental_condition_symbols (
    dental_condition_symbol_id BIGSERIAL PRIMARY KEY,
    dental_condition_id BIGINT NOT NULL REFERENCES dental_conditions(dental_condition_id),
    status VARCHAR(8) NOT NULL,
    shape VARCHAR(24) NOT NULL,
    color CHAR(7) NOT NULL,
    secondary_color CHAR(7),
    filled BOOLEAN NOT NULL DEFAULT FALSE,
    line_style VARCHAR(6) NOT NULL DEFAULT 'SOLID',
    CONSTRAINT uq_condition_status UNIQUE (dental_condition_id, status),
    CONSTRAINT chk_symbol_status CHECK (status IN ('PENDING','DONE')),
    CONSTRAINT chk_symbol_shape CHECK (shape IN ('ASTERISK', 'CROSS', 'CIRCLE', 'CIRCLE_CROSS', 'TRIANGLE', 'DOUBLE_SQUARE', 'RECTANGLE', 'SQUARES_LINE', 'DOTS_PARENTHESES', 'EQUALS', 'NUMBER')),
    CONSTRAINT chk_symbol_line_style CHECK (line_style IN ('SOLID','DOTTED')),
    CONSTRAINT chk_symbol_color CHECK (color ~ '^#[0-9A-F]{6}$'),
    CONSTRAINT chk_symbol_secondary_color CHECK (secondary_color IS NULL OR secondary_color ~ '^#[0-9A-F]{6}$')
);

CREATE TABLE dental_findings (
    dental_finding_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id UUID NOT NULL REFERENCES encounters(encounter_id),
    tooth_code SMALLINT NOT NULL REFERENCES teeth(tooth_code),
    end_tooth_code SMALLINT REFERENCES teeth(tooth_code),
    surface_code CHAR(1) REFERENCES tooth_surfaces(surface_code),
    dental_condition_id BIGINT NOT NULL REFERENCES dental_conditions(dental_condition_id),
    status VARCHAR(8) NOT NULL,
    grade SMALLINT,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    voided_at TIMESTAMPTZ,
    CONSTRAINT chk_finding_status CHECK (status IN ('PENDING','DONE')),
    CONSTRAINT chk_finding_end_tooth CHECK (end_tooth_code IS NULL OR (surface_code IS NULL AND end_tooth_code <> tooth_code))
);

CREATE INDEX idx_dental_findings_encounter_id ON dental_findings(encounter_id);
CREATE INDEX idx_dental_findings_tooth_code ON dental_findings(tooth_code);

-- Semillas de teeth
INSERT INTO teeth (tooth_code, dentition, arch, quadrant, position, tooth_type) VALUES
-- Permanentes superiores (1, 2)
(11, 'PERMANENT', 'UPPER', 1, 1, 'INCISOR'), (12, 'PERMANENT', 'UPPER', 1, 2, 'INCISOR'),
(13, 'PERMANENT', 'UPPER', 1, 3, 'CANINE'), (14, 'PERMANENT', 'UPPER', 1, 4, 'PREMOLAR'),
(15, 'PERMANENT', 'UPPER', 1, 5, 'PREMOLAR'), (16, 'PERMANENT', 'UPPER', 1, 6, 'MOLAR'),
(17, 'PERMANENT', 'UPPER', 1, 7, 'MOLAR'), (18, 'PERMANENT', 'UPPER', 1, 8, 'MOLAR'),
(21, 'PERMANENT', 'UPPER', 2, 1, 'INCISOR'), (22, 'PERMANENT', 'UPPER', 2, 2, 'INCISOR'),
(23, 'PERMANENT', 'UPPER', 2, 3, 'CANINE'), (24, 'PERMANENT', 'UPPER', 2, 4, 'PREMOLAR'),
(25, 'PERMANENT', 'UPPER', 2, 5, 'PREMOLAR'), (26, 'PERMANENT', 'UPPER', 2, 6, 'MOLAR'),
(27, 'PERMANENT', 'UPPER', 2, 7, 'MOLAR'), (28, 'PERMANENT', 'UPPER', 2, 8, 'MOLAR'),
-- Permanentes inferiores (3, 4)
(31, 'PERMANENT', 'LOWER', 3, 1, 'INCISOR'), (32, 'PERMANENT', 'LOWER', 3, 2, 'INCISOR'),
(33, 'PERMANENT', 'LOWER', 3, 3, 'CANINE'), (34, 'PERMANENT', 'LOWER', 3, 4, 'PREMOLAR'),
(35, 'PERMANENT', 'LOWER', 3, 5, 'PREMOLAR'), (36, 'PERMANENT', 'LOWER', 3, 6, 'MOLAR'),
(37, 'PERMANENT', 'LOWER', 3, 7, 'MOLAR'), (38, 'PERMANENT', 'LOWER', 3, 8, 'MOLAR'),
(41, 'PERMANENT', 'LOWER', 4, 1, 'INCISOR'), (42, 'PERMANENT', 'LOWER', 4, 2, 'INCISOR'),
(43, 'PERMANENT', 'LOWER', 4, 3, 'CANINE'), (44, 'PERMANENT', 'LOWER', 4, 4, 'PREMOLAR'),
(45, 'PERMANENT', 'LOWER', 4, 5, 'PREMOLAR'), (46, 'PERMANENT', 'LOWER', 4, 6, 'MOLAR'),
(47, 'PERMANENT', 'LOWER', 4, 7, 'MOLAR'), (48, 'PERMANENT', 'LOWER', 4, 8, 'MOLAR'),
-- Temporales superiores (5, 6)
(51, 'DECIDUOUS', 'UPPER', 5, 1, 'INCISOR'), (52, 'DECIDUOUS', 'UPPER', 5, 2, 'INCISOR'),
(53, 'DECIDUOUS', 'UPPER', 5, 3, 'CANINE'), (54, 'DECIDUOUS', 'UPPER', 5, 4, 'MOLAR'),
(55, 'DECIDUOUS', 'UPPER', 5, 5, 'MOLAR'),
(61, 'DECIDUOUS', 'UPPER', 6, 1, 'INCISOR'), (62, 'DECIDUOUS', 'UPPER', 6, 2, 'INCISOR'),
(63, 'DECIDUOUS', 'UPPER', 6, 3, 'CANINE'), (64, 'DECIDUOUS', 'UPPER', 6, 4, 'MOLAR'),
(65, 'DECIDUOUS', 'UPPER', 6, 5, 'MOLAR'),
-- Temporales inferiores (7, 8)
(71, 'DECIDUOUS', 'LOWER', 7, 1, 'INCISOR'), (72, 'DECIDUOUS', 'LOWER', 7, 2, 'INCISOR'),
(73, 'DECIDUOUS', 'LOWER', 7, 3, 'CANINE'), (74, 'DECIDUOUS', 'LOWER', 7, 4, 'MOLAR'),
(75, 'DECIDUOUS', 'LOWER', 7, 5, 'MOLAR'),
(81, 'DECIDUOUS', 'LOWER', 8, 1, 'INCISOR'), (82, 'DECIDUOUS', 'LOWER', 8, 2, 'INCISOR'),
(83, 'DECIDUOUS', 'LOWER', 8, 3, 'CANINE'), (84, 'DECIDUOUS', 'LOWER', 8, 4, 'MOLAR'),
(85, 'DECIDUOUS', 'LOWER', 8, 5, 'MOLAR');

-- Semillas de tooth_surfaces
INSERT INTO tooth_surfaces (surface_code, name) VALUES
('O', 'Oclusal'),
('V', 'Vestibular'),
('L', 'Lingual'),
('M', 'Mesial'),
('D', 'Distal');

-- Semillas de dental_conditions
INSERT INTO dental_conditions (dental_condition_id, code, name, category, scope, grade_min, grade_max, cpo_component, ceo_component) VALUES
(1, 'MOBILITY', 'Movilidad', 'PERIODONTAL', 'TOOTH', 0, 3, NULL, NULL),
(2, 'RECESSION', 'Recesión gingival', 'PERIODONTAL', 'TOOTH', 1, 4, NULL, NULL),
(3, 'CARIES', 'Caries', 'DENTAL', 'SURFACE', NULL, NULL, 'C', 'c'),
(4, 'FILLING', 'Obturación', 'DENTAL', 'SURFACE', NULL, NULL, 'O', 'o'),
(5, 'SEALANT', 'Sellante', 'DENTAL', 'SURFACE', NULL, NULL, NULL, NULL),
(6, 'EXTRACTION', 'Extracción indicada', 'DENTAL', 'TOOTH', NULL, NULL, 'P', 'e'),
(7, 'MISSING_CARIES', 'Pérdida por caries', 'DENTAL', 'TOOTH', NULL, NULL, 'P', NULL),
(8, 'MISSING_OTHER', 'Pérdida por otra causa', 'DENTAL', 'TOOTH', NULL, NULL, NULL, NULL),
(9, 'ROOT_CANAL', 'Endodoncia', 'DENTAL', 'TOOTH', NULL, NULL, NULL, NULL),
(10, 'CROWN', 'Corona', 'DENTAL', 'TOOTH', NULL, NULL, 'O', 'o'),
(11, 'FIXED_PROSTHESIS', 'Prótesis fija', 'DENTAL', 'SPAN', NULL, NULL, NULL, NULL),
(12, 'REMOVABLE_PROSTHESIS', 'Prótesis removible', 'DENTAL', 'SPAN', NULL, NULL, NULL, NULL),
(13, 'TOTAL_PROSTHESIS', 'Prótesis total', 'DENTAL', 'SPAN', NULL, NULL, NULL, NULL),
(14, 'OTHER', 'Otro (patología o tratamiento)', 'DENTAL', 'TOOTH', NULL, NULL, NULL, NULL);
SELECT setval('dental_conditions_dental_condition_id_seq', 14);

-- Semillas de dental_condition_symbols
INSERT INTO dental_condition_symbols (dental_condition_id, status, shape, color, secondary_color, filled, line_style) VALUES
(1, 'DONE', 'NUMBER', '#1E88E5', NULL, false, 'SOLID'),
(2, 'DONE', 'NUMBER', '#1E88E5', NULL, false, 'SOLID'),
(3, 'PENDING', 'CIRCLE', '#D32F2F', NULL, false, 'SOLID'),
(4, 'DONE', 'CIRCLE', '#1E88E5', NULL, false, 'SOLID'),
(5, 'PENDING', 'ASTERISK', '#D32F2F', NULL, false, 'SOLID'),
(5, 'DONE', 'ASTERISK', '#1E88E5', NULL, false, 'SOLID'),
(6, 'PENDING', 'CROSS', '#D32F2F', NULL, false, 'SOLID'),
(7, 'DONE', 'CROSS', '#1E88E5', NULL, false, 'SOLID'),
(8, 'DONE', 'CIRCLE_CROSS', '#1E88E5', '#000000', false, 'SOLID'),
(9, 'PENDING', 'TRIANGLE', '#D32F2F', NULL, false, 'SOLID'),
(9, 'DONE', 'TRIANGLE', '#1E88E5', NULL, false, 'SOLID'),
(10, 'DONE', 'DOUBLE_SQUARE', '#1E88E5', NULL, false, 'SOLID'),
(11, 'DONE', 'SQUARES_LINE', '#000000', NULL, false, 'DOTTED'),
(12, 'DONE', 'DOTS_PARENTHESES', '#000000', NULL, false, 'SOLID'),
(13, 'DONE', 'EQUALS', '#000000', NULL, false, 'SOLID'),
(14, 'PENDING', 'RECTANGLE', '#D32F2F', NULL, true, 'SOLID'),
(14, 'DONE', 'RECTANGLE', '#1E88E5', NULL, true, 'SOLID');
