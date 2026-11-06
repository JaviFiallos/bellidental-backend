# 035 — Número de receta (R-000001) en el PDF

## Objetivo
Pedido del doctor: cada receta lleva un número visible para emparejar la mitad de la farmacia con la del paciente
(D-34) y ubicarla cuando llamen. Formato `R-000001`, correlativo de toda la clínica, igual que el comprobante
(`C-000001`, `billing/domain/ReceiptNumber`).

Reglas (D-35):
- El número se asigna **al emitir** (`POST /prescriptions/{id}/issue`), no al crear el borrador: los borradores se
  descartan y dejarían huecos. Un DRAFT no tiene número.
- Una receta anulada conserva su número; nunca se reutiliza.
- Las recetas ya emitidas se numeran en la migración por fecha de emisión, para que al reimprimirlas también salga.

## Archivos a tocar
```
src/main/resources/db/migration/
  V23__prescription_number.sql       secuencia, columna, CHECK, backfill, columna en prescriptions_aud
docs/schema.dbml                     prescriptions.prescription_number
src/main/java/com/bellidental/persistence/
  entity/Prescription.java           + prescriptionNumber
  repository/PrescriptionRepository.java   + nextPrescriptionNumber() (nextval, como PaymentRepository)
src/main/java/com/bellidental/prescriptions/
  domain/PrescriptionNumber.java     format(long) → "R-%06d" (copia del patrón de ReceiptNumber)
  application/PrescriptionService.java     issue(): asigna el número; toResponse lo incluye
  application/PrescriptionPdfService.java  "N.º R-000123" bajo CONFIDENCIAL en ambas mitades
  application/PrescriptionPdfPalette.java  + FONT_PRESCRIPTION_NUMBER
  api/dto/PrescriptionResponse.java  + prescriptionNumber (null en DRAFT)
src/test/java/com/bellidental/prescriptions/
  PrescriptionFlowIT.java            aserciones en el caso principal (sin casos nuevos)
  PrescriptionPdfIT.java             aserción en el caso 1 (sin casos nuevos)
docs/IMPLEMENTATION-PLAN.md          §4.7, fila 035, decisión D-35
```
No tocar `PrescriptionSummary`/`PrescriptionDirectory` ni el frontend.

## Criterios de aceptación
1. **Migración V23** (no editar migraciones anteriores):
   - `CREATE SEQUENCE prescription_number_seq;`
   - `ALTER TABLE prescriptions ADD COLUMN prescription_number varchar(30) UNIQUE;`
   - Backfill de las ISSUED (anuladas incluidas) en orden `prescribed_at, created_at, prescription_id`:
     `'R-' || lpad(n::text, 6, '0')` con `row_number()`; luego `setval` de la secuencia al total numerado (si no hay
     ninguna, la secuencia queda sin usar y el primero será `R-000001`).
   - `CHECK (status <> 'ISSUED' OR prescription_number IS NOT NULL)` (constraint con nombre
     `ck_prescriptions_issued_number`), añadido **después** del backfill.
   - `ALTER TABLE prescriptions_aud ADD COLUMN prescription_number varchar(30);`
2. `docs/schema.dbml`: en `prescriptions`, tras `status`,
   `prescription_number varchar(30) [unique, note: 'R-000001; se asigna al emitir (D-35)']`.
3. **Emitir:** en `issue()`, tras validar con `PrescriptionRules`, `prescription.setPrescriptionNumber(
   PrescriptionNumber.format(prescriptionRepository.nextPrescriptionNumber()))`. Solo ahí se asigna; ningún otro
   método lo cambia.
4. **API:** `PrescriptionResponse.prescriptionNumber` (`String`, `null` en DRAFT). Todos los endpoints que devuelven
   la receta lo traen por `toResponse`.
5. **PDF:** en `createHalfHeader`, debajo de "CONFIDENCIAL", alineado a la derecha: `"N.º " + prescriptionNumber`
   con `FONT_PRESCRIPTION_NUMBER` (Helvetica 9 bold, `BLUE_800`). Aparece en **ambas** mitades. El PDF solo se genera
   para ISSUED, así que el número siempre existe.
6. Plan (`docs/IMPLEMENTATION-PLAN.md`):
   - §4.7, tras la línea de formato de la 034: «Número de receta (tarea 035, D-35): `R-000001` correlativo de la
     clínica (`prescription_number_seq`), asignado al emitir; DRAFT sin número; anulada lo conserva; en el PDF bajo
     CONFIDENCIAL en ambas mitades.»
   - Fila `| 035 | Número de receta R-000001 al emitir y en el PDF | V23 | 034 | PrescriptionFlowIT + PrescriptionPdfIT (aserciones) |` tras la 034.
   - Decisión `| D-35 | Número de receta `R-000001` correlativo de toda la clínica, asignado al emitir (no al crear el borrador); la anulada lo conserva; las ISSUED previas se numeran en V23 por fecha de emisión | tarea 035 |` tras D-34.
7. `./mvnw -q clean verify` pasa.

## Tests requeridos
- **Unit:** ninguno.
- **Integration** (solo aserciones nuevas en casos existentes; no crear casos):
  1. `PrescriptionFlowIT`, caso principal (crear → emitir): al crear, `prescriptionNumber` es `null`; tras emitir,
     coincide con `^R-\d{6}$`.
  2. `PrescriptionPdfIT`, caso 1: el `prescriptionNumber` de la receta emitida aparece al menos 2 veces en el texto
     del PDF (una por mitad). Regenerar `target/prescription-sample.pdf`.

## Resultado

**Archivos creados:** `db/migration/V23__prescription_number.sql`, `prescriptions/domain/PrescriptionNumber.java`.

**Archivos modificados:** `persistence/entity/Prescription.java` (campo `prescriptionNumber`),
`persistence/repository/PrescriptionRepository.java` (`nextPrescriptionNumber()`),
`prescriptions/application/PrescriptionService.java` (asigna en `issue()`, `toResponse` lo incluye),
`prescriptions/application/PrescriptionPdfService.java` (`N.º` bajo CONFIDENCIAL en ambas mitades),
`prescriptions/application/PrescriptionPdfPalette.java` (`FONT_PRESCRIPTION_NUMBER`),
`prescriptions/api/dto/PrescriptionResponse.java`, `docs/schema.dbml`, `docs/IMPLEMENTATION-PLAN.md`,
`PrescriptionFlowIT.java` y `PrescriptionPdfIT.java` (solo aserciones en los casos existentes).

**Tests (`./mvnw -q clean verify`, exit 0):**
- Surefire: 103 run, 0 failures, 0 errors, 0 skipped.
- Failsafe: 101 run, 0 failures, 0 errors, 0 skipped (PrescriptionFlowIT 3/3, PrescriptionPdfIT 5/5).
- `target/prescription-sample.pdf` regenerado.

**Supuestos y problemas:**
- Backfill: `setval` solo se ejecuta si hay recetas numeradas (`setval` con 0 daría error); sin ninguna, el primero será `R-000001`.
- La columna `prescription_number` no lleva `NOT NULL` en DRAFT, así que la restricción es el `CHECK ck_prescriptions_issued_number`.
- La entidad `Prescription` (`@Audited`) tiene la columna nueva en `prescriptions_aud` por V23.
- Nombre del repositorio: `nextPrescriptionNumber()` según la tarea (PaymentRepository usa `nextReceiptSequence`).
- No se hizo commit; no se tocó frontend ni otros módulos.

## Revisión
Estado: APROBADO
- V23: backfill con `row_number()` en orden `prescribed_at, created_at, prescription_id` solo de ISSUED (anuladas incluidas), `setval` al total (omitido si 0 → R-000001), CHECK `ck_prescriptions_issued_number` tras el backfill, columna en `prescriptions_aud`.
- El número se asigna solo en `issue()` tras `PrescriptionRules.checkCanIssue`; `toResponse` lo expone (null en DRAFT). Plan, D-35 y dbml según la tarea.
- PDF revisado: "N.º R-000006" bajo CONFIDENCIAL en ambas mitades, cabecera sin descuadre.
- Totales confirmados en los reports: surefire 103/0/0, failsafe 101/0/0.
