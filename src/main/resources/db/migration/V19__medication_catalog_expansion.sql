-- V19__medication_catalog_expansion.sql
-- Amplía el catálogo de medicamentos de uso odontológico (RF04) con sus posologías predefinidas.
-- Idempotente: se puede aplicar sobre una BD que ya tenga estos datos (se cargó primero en local).
-- Solo medicamentos de receta común: los de receta especial (estupefacientes y psicotrópicos) quedan fuera.
-- Pediátricos: la dosis va por kg; el odontólogo la convierte a ml al recetar.

-- 1. Correcciones a la semilla de V10 ---------------------------------------------------------

-- La anestesia local se aplica en el consultorio; no es un medicamento que se entregue en una receta.
UPDATE medications SET active = FALSE
WHERE name = 'Lidocaína con epinefrina' AND concentration = '2 % / 1:100.000';

-- 500 mg cada 12 h queda por debajo de los esquemas para infección odontogénica (500 mg c/8 h o 1 g c/12 h).
UPDATE medication_presets
SET label = '1 g cada 12 h × 7 días', dose = '2 cápsulas', duration = '7 días'
WHERE label = 'Cada 12 h × 5 días'
  AND medication_id = (SELECT medication_id FROM medications WHERE name = 'Amoxicilina' AND concentration = '500 mg');

-- La pasta dental inactiva la clorhexidina: el enjuague va 30 minutos después del cepillado.
UPDATE medication_presets
SET instructions = 'Enjuagar 30 segundos, 30 minutos después del cepillado. No tragar ni comer en los 30 minutos siguientes. Puede manchar los dientes.'
WHERE medication_id = (SELECT medication_id FROM medications WHERE name = 'Clorhexidina' AND concentration = '0,12 %');

UPDATE medication_presets
SET instructions = 'No exceder 4 g (8 tabletas) al día.'
WHERE label = 'Cada 6 h × 3 días'
  AND medication_id = (SELECT medication_id FROM medications WHERE name = 'Paracetamol' AND concentration = '500 mg');

-- 2. Medicamentos nuevos ---------------------------------------------------------------------

INSERT INTO medications (name, presentation, concentration, route) VALUES
-- Antibióticos
('Amoxicilina', 'Tableta', '875 mg', 'Oral'),
('Amoxicilina', 'Suspensión oral', '250 mg/5 ml', 'Oral'),
('Amoxicilina + ácido clavulánico', 'Tableta', '500 mg/125 mg', 'Oral'),
('Amoxicilina + ácido clavulánico', 'Tableta', '875 mg/125 mg', 'Oral'),
('Amoxicilina + ácido clavulánico', 'Suspensión oral', '400 mg/57 mg/5 ml', 'Oral'),
('Azitromicina', 'Tableta', '500 mg', 'Oral'),
('Azitromicina', 'Suspensión oral', '200 mg/5 ml', 'Oral'),
('Cefalexina', 'Cápsula', '500 mg', 'Oral'),
('Doxiciclina', 'Cápsula', '100 mg', 'Oral'),
-- Analgésicos y antiinflamatorios
('Ibuprofeno', 'Tableta', '600 mg', 'Oral'),
('Ibuprofeno', 'Suspensión oral', '100 mg/5 ml', 'Oral'),
('Paracetamol', 'Suspensión oral', '160 mg/5 ml', 'Oral'),
('Diclofenaco potásico', 'Tableta', '50 mg', 'Oral'),
('Naproxeno sódico', 'Tableta', '550 mg', 'Oral'),
('Etoricoxib', 'Tableta', '90 mg', 'Oral'),
('Metamizol sódico', 'Tableta', '500 mg', 'Oral'),
('Dexketoprofeno', 'Tableta', '25 mg', 'Oral'),
-- Corticoide
('Dexametasona', 'Tableta', '4 mg', 'Oral'),
-- Antivirales y antifúngicos
('Aciclovir', 'Tableta', '200 mg', 'Oral'),
('Aciclovir', 'Crema', '5 %', 'Tópica'),
('Nistatina', 'Suspensión oral', '100.000 UI/ml', 'Bucal'),
('Fluconazol', 'Cápsula', '150 mg', 'Oral'),
('Miconazol', 'Gel oral', '2 %', 'Bucal'),
-- Uso tópico bucal
('Triamcinolona acetonida', 'Pasta oral', '0,1 %', 'Bucal'),
('Bencidamina', 'Enjuague bucal', '0,15 %', 'Bucal'),
-- Otros
('Ácido tranexámico', 'Tableta', '500 mg', 'Oral'),
('Omeprazol', 'Cápsula', '20 mg', 'Oral')
ON CONFLICT (name, concentration, presentation) DO NOTHING;

-- 3. Posologías predefinidas (presets) -------------------------------------------------------

INSERT INTO medication_presets (medication_id, label, dose, frequency, duration, instructions)
SELECT m.medication_id, v.label, v.dose, v.frequency, v.duration, v.instructions
FROM (VALUES
    ('Amoxicilina', '500 mg', 'Cápsula',
     'Profilaxis de endocarditis', '4 cápsulas (2 g)', 'dosis única', 'sesión única',
     'Tomar 30 a 60 minutos antes del procedimiento.'),
    ('Amoxicilina', '875 mg', 'Tableta',
     'Cada 12 h × 7 días', '1 tableta', 'cada 12 horas', '7 días',
     'Tomar con alimentos.'),
    ('Amoxicilina', '250 mg/5 ml', 'Suspensión oral',
     'Pediátrica cada 8 h × 7 días', 'según peso: 50 mg/kg/día en 3 tomas', 'cada 8 horas', '7 días',
     'Máximo 500 mg por toma. Agitar antes de usar y conservar en refrigeración.'),
    ('Amoxicilina + ácido clavulánico', '500 mg/125 mg', 'Tableta',
     'Cada 8 h × 7 días', '1 tableta', 'cada 8 horas', '7 días',
     'Tomar al inicio de las comidas.'),
    ('Amoxicilina + ácido clavulánico', '875 mg/125 mg', 'Tableta',
     'Cada 12 h × 7 días', '1 tableta', 'cada 12 horas', '7 días',
     'Tomar al inicio de las comidas.'),
    ('Amoxicilina + ácido clavulánico', '400 mg/57 mg/5 ml', 'Suspensión oral',
     'Pediátrica cada 12 h × 7 días', 'según peso: 45 mg/kg/día en 2 tomas', 'cada 12 horas', '7 días',
     'Tomar al inicio de las comidas. Agitar antes de usar, refrigerar y desechar a los 7 días de preparada.'),
    ('Azitromicina', '500 mg', 'Tableta',
     'Cada 24 h × 3 días', '1 tableta', 'cada 24 horas', '3 días',
     'Alternativa en alergia a la penicilina.'),
    ('Azitromicina', '500 mg', 'Tableta',
     'Profilaxis (alergia a penicilina)', '1 tableta', 'dosis única', 'sesión única',
     'Tomar 30 a 60 minutos antes del procedimiento.'),
    ('Azitromicina', '200 mg/5 ml', 'Suspensión oral',
     'Pediátrica cada 24 h × 3 días', 'según peso: 10 mg/kg por toma', 'cada 24 horas', '3 días',
     'Máximo 500 mg al día. Agitar antes de usar.'),
    ('Cefalexina', '500 mg', 'Cápsula',
     'Cada 6 h × 7 días', '1 cápsula', 'cada 6 horas', '7 días',
     NULL),
    ('Doxiciclina', '100 mg', 'Cápsula',
     'Cada 12 h × 7 días', '1 cápsula', 'cada 12 horas', '7 días',
     'Tomar con abundante agua y no acostarse en 30 minutos. Evitar lácteos y el sol. No usar en embarazo ni en menores de 8 años.'),
    ('Ibuprofeno', '600 mg', 'Tableta',
     'Cada 8 h × 3 días', '1 tableta', 'cada 8 horas', '3 días',
     'Tomar después de los alimentos. Máximo 2.400 mg al día.'),
    ('Ibuprofeno', '100 mg/5 ml', 'Suspensión oral',
     'Pediátrica cada 8 h × 3 días', 'según peso: 10 mg/kg por toma', 'cada 8 horas', '3 días',
     'Desde los 6 meses de edad. Tomar con alimentos. Máximo 40 mg/kg al día.'),
    ('Paracetamol', '500 mg', 'Tableta',
     '1 g cada 8 h × 3 días', '2 tabletas', 'cada 8 horas', '3 días',
     'No exceder 4 g (8 tabletas) al día.'),
    ('Paracetamol', '160 mg/5 ml', 'Suspensión oral',
     'Pediátrica cada 6 h × 3 días', 'según peso: 15 mg/kg por toma', 'cada 6 horas', '3 días',
     'No más de 5 tomas (75 mg/kg) al día.'),
    ('Diclofenaco potásico', '50 mg', 'Tableta',
     'Cada 8 h × 3 días', '1 tableta', 'cada 8 horas', '3 días',
     'Tomar después de los alimentos.'),
    ('Naproxeno sódico', '550 mg', 'Tableta',
     'Cada 12 h × 3 días', '1 tableta', 'cada 12 horas', '3 días',
     'Tomar después de los alimentos.'),
    ('Etoricoxib', '90 mg', 'Tableta',
     'Cada 24 h × 3 días', '1 tableta', 'cada 24 horas', '3 días',
     'Máximo 3 días para dolor dental agudo. Precaución en hipertensión.'),
    ('Metamizol sódico', '500 mg', 'Tableta',
     'Cada 8 h × 3 días', '1 tableta', 'cada 8 horas', '3 días',
     'Suspender y consultar si presenta fiebre, dolor de garganta o llagas en la boca.'),
    ('Dexketoprofeno', '25 mg', 'Tableta',
     'Cada 8 h × 3 días', '1 tableta', 'cada 8 horas', '3 días',
     'Tomar con alimentos. Máximo 3 tabletas al día.'),
    ('Dexametasona', '4 mg', 'Tableta',
     'Cada 24 h × 3 días', '1 tableta', 'cada 24 horas', '3 días',
     'Tomar en la mañana con alimentos.'),
    ('Dexametasona', '4 mg', 'Tableta',
     'Dosis única preoperatoria', '2 tabletas (8 mg)', 'dosis única', 'sesión única',
     'Tomar 1 hora antes de la cirugía.'),
    ('Aciclovir', '200 mg', 'Tableta',
     '5 veces al día × 5 días', '1 tableta', '5 veces al día (cada 4 h, sin dosis nocturna)', '5 días',
     'Iniciar ante los primeros síntomas.'),
    ('Aciclovir', '5 %', 'Crema',
     '5 veces al día × 5 días', 'capa fina sobre la lesión', '5 veces al día', '5 días',
     'Iniciar ante el primer hormigueo. Lavarse las manos después de aplicar.'),
    ('Nistatina', '100.000 UI/ml', 'Suspensión oral',
     'Cada 6 h × 14 días', '5 ml', 'cada 6 horas', '14 días',
     'Enjuagar 1 minuto y luego tragar, después de las comidas. Retirar y desinfectar la prótesis.'),
    ('Fluconazol', '150 mg', 'Cápsula',
     'Semanal × 2 dosis', '1 cápsula', 'cada 7 días', '2 dosis',
     NULL),
    ('Miconazol', '2 %', 'Gel oral',
     'Cada 6 h × 14 días', '2,5 ml (½ cucharadita)', 'cada 6 horas', '14 días',
     'Mantener en la boca antes de tragar; aplicar también en la prótesis. No usar si toma warfarina.'),
    ('Triamcinolona acetonida', '0,1 %', 'Pasta oral',
     'Cada 8 h × 7 días', 'capa fina sobre la lesión', 'cada 8 horas', '7 días',
     'Aplicar sin frotar, después de las comidas y antes de dormir.'),
    ('Bencidamina', '0,15 %', 'Enjuague bucal',
     'Cada 8 h × 7 días', '15 ml', 'cada 8 horas', '7 días',
     'Enjuagar o hacer gárgaras 30 segundos y escupir. No tragar.'),
    ('Ácido tranexámico', '500 mg', 'Tableta',
     'Cada 8 h × 3 días', '1 tableta', 'cada 8 horas', '3 días',
     'Para el control del sangrado después de la extracción.'),
    ('Omeprazol', '20 mg', 'Cápsula',
     'Cada 24 h con el antiinflamatorio', '1 cápsula', 'cada 24 horas', 'mientras tome el antiinflamatorio',
     'Tomar en ayunas, 30 minutos antes del desayuno.')
) AS v(name, concentration, presentation, label, dose, frequency, duration, instructions)
JOIN medications m
  ON m.name = v.name AND m.concentration = v.concentration AND m.presentation = v.presentation
WHERE NOT EXISTS (
    SELECT 1 FROM medication_presets p
    WHERE p.medication_id = m.medication_id AND p.label = v.label
);
