-- V23__prescription_number.sql
-- Número de receta R-000001 (D-35). Se asigna al emitir; las ISSUED previas se numeran por fecha de emisión.

CREATE SEQUENCE prescription_number_seq;

ALTER TABLE prescriptions ADD COLUMN prescription_number varchar(30) UNIQUE;

UPDATE prescriptions p
SET prescription_number = 'R-' || lpad(numbered.position::text, 6, '0')
FROM (
    SELECT prescription_id,
           row_number() OVER (ORDER BY prescribed_at, created_at, prescription_id) AS position
    FROM prescriptions
    WHERE status = 'ISSUED'
) numbered
WHERE p.prescription_id = numbered.prescription_id;

SELECT setval('prescription_number_seq', numbered_total.total)
FROM (SELECT count(*) AS total FROM prescriptions WHERE prescription_number IS NOT NULL) numbered_total
WHERE numbered_total.total > 0;

ALTER TABLE prescriptions ADD CONSTRAINT ck_prescriptions_issued_number
    CHECK (status <> 'ISSUED' OR prescription_number IS NOT NULL);

ALTER TABLE prescriptions_aud ADD COLUMN prescription_number varchar(30);
