-- V22__orthodontics_catalog.sql
-- Catálogo de ortodoncia: diagnóstico CIE-10 Z46.4, especialidad Ortodoncia (15 min) y tratamientos D8080 y D8670.
-- Tarea 030 (G-17 del frontend, D-32).

-- 1. Diagnóstico CIE-10 para control de ortodoncia
INSERT INTO icd10_codes (code, description, category)
VALUES ('Z46.4', 'Prueba y ajuste de dispositivo ortodóntico', 'Z46 Prueba y ajuste de otros dispositivos')
ON CONFLICT (code) DO NOTHING;

-- 2. Especialidad Ortodoncia (duración corta de 15 min para no perder huecos en agenda)
INSERT INTO specialties (name, default_duration_minutes, active)
VALUES ('Ortodoncia', 15, true)
ON CONFLICT (name) DO NOTHING;

SELECT setval('specialties_specialty_id_seq', (SELECT MAX(specialty_id) FROM specialties));

-- 3. Tratamientos de ortodoncia en el catálogo
INSERT INTO treatment_catalog (code, name, description, base_price, specialty_id)
VALUES
('D8080', 'Ortodoncia (tratamiento completo)', 'Monto total acordado; se paga con abonos', 1200.00, (SELECT specialty_id FROM specialties WHERE name = 'Ortodoncia')),
('D8670', 'Control de ortodoncia (cambio de ligas)', 'Control periódico; sin cargo (cubierto por el tratamiento completo)', 0.00, (SELECT specialty_id FROM specialties WHERE name = 'Ortodoncia'))
ON CONFLICT (code) DO NOTHING;
