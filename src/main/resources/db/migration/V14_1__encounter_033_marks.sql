-- V14_1__encounter_033_marks.sql
-- Casillas del 033 (regiones del examen y exámenes del plan) y descripción única de planes

-- 1. Nuevas columnas y constraints
ALTER TABLE encounters
    ADD COLUMN stomatognathic_regions smallint[]    NOT NULL DEFAULT '{}',
    ADD COLUMN diagnostic_exams       varchar(20)[] NOT NULL DEFAULT '{}',
    ADD COLUMN plan_description       text,
    ADD CONSTRAINT chk_encounters_stomatognathic_regions
        CHECK (stomatognathic_regions <@ ARRAY[1,2,3,4,5,6,7,8,9,10,11,12]::smallint[]),
    ADD CONSTRAINT chk_encounters_diagnostic_exams
        CHECK (diagnostic_exams <@ ARRAY['BIOMETRIA','QUIMICA_SANGUINEA','RAYOS_X','OTROS']::varchar(20)[]);

-- 2. Relleno de diagnostic_exams desde diagnostic_plan
UPDATE encounters
SET diagnostic_exams = array_remove(ARRAY[
    CASE WHEN diagnostic_plan ~* 'biometr[ií]a' THEN 'BIOMETRIA'::varchar(20) ELSE NULL END,
    CASE WHEN diagnostic_plan ~* 'qu[ií]mica sangu[ií]nea' THEN 'QUIMICA_SANGUINEA'::varchar(20) ELSE NULL END,
    CASE WHEN diagnostic_plan ~* '\mrx\M|rayos[ -]x' THEN 'RAYOS_X'::varchar(20) ELSE NULL END
], NULL)
WHERE diagnostic_plan IS NOT NULL;

-- 3. Unión de los tres textos en plan_description
UPDATE encounters
SET plan_description = NULLIF(concat_ws(E'\n',
    NULLIF(btrim(diagnostic_plan), ''),
    'Terapéutico: ' || NULLIF(btrim(therapeutic_plan), ''),
    'Educacional: ' || NULLIF(btrim(educational_plan), '')), '');

-- 4. Eliminación de columnas obsoletas
ALTER TABLE encounters
    DROP COLUMN diagnostic_plan,
    DROP COLUMN therapeutic_plan,
    DROP COLUMN educational_plan;
