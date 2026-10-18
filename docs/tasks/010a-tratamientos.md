# 010a — Tratamientos: catálogo y registro en la atención (V9)

## Objetivo
Módulo nuevo `com.bellidental.treatments` (RF04, plan §4.6). El odontólogo busca en el catálogo y registra los
tratamientos realizados en una atención, con precio copiado del catálogo (editable para descuentos), y los anula
con motivo. La 010 se divide en dos: aquí van el catálogo, el registro y la anulación. El vínculo con hallazgos del
odontograma y el DONE automático van en la 010b.

Se aplica la misma regla de modificación que en diagnósticos y odontograma (D-6): solo el odontólogo que atendió,
en una atención no anulada, también después del cierre. Se usa `ClinicalDirectory.requireModifiableEncounter`, que
ya existe.

Fuera de alcance: `dental_finding_id` (010b) y `ChargeLedger`, que pasa a la 012, la tarea que lo consume.

## Archivos a tocar
```
src/main/resources/db/migration/V9__treatments.sql      (nuevo)
persistence/entity/
  Treatment.java              treatment_catalog                  (nuevo)
  PerformedTreatment.java     performed_treatments               (nuevo)
persistence/repository/
  TreatmentRepository.java, PerformedTreatmentRepository.java    (nuevos)
clinical/                                                        (cambio mínimo)
  ClinicalDirectory.java      + Optional<DiagnosisSummary> findDiagnosis(UUID encounterDiagnosisId)
  DiagnosisSummary.java       (nuevo) record: id, encounterId, icd10Code, icd10Description, voided
  application/ClinicalDirectoryImpl.java   implementa el método
treatments/                                                      (nuevo)
  api/TreatmentController.java
  api/dto/
    TreatmentResponse.java                 id, code, name, description, basePrice
    RecordTreatmentRequest.java            treatmentId, quantity, price, encounterDiagnosisId, toothCode, surfaceCode, notes
    PerformedTreatmentResponse.java        id, encounterId, treatmentId, treatmentCode, treatmentName,
                                           encounterDiagnosisId, toothCode, surfaceCode, quantity, price, total,
                                           notes, createdAt
    VoidTreatmentRequest.java              voidReason
  application/TreatmentService.java
src/test/java/com/bellidental/treatments/TreatmentFlowIT.java    (nuevo)
docs/IMPLEMENTATION-PLAN.md       tabla §3: filas 010a/010b; §4.6: ChargeLedger pasa a la 012
```
No se crea `TreatmentDirectory` ni `domain/`: no hay reglas puras que lo justifiquen.

## Criterios de aceptación
1. **V9** según `docs/schema.dbml` §8 (`treatment_catalog`, `performed_treatments`, FKs de las `Ref`), más
   `CHECK (quantity >= 1)`, `CHECK (price >= 0)`, `CHECK (base_price >= 0)` e índice GIN trigram sobre
   `treatment_catalog.name` (`pg_trgm` ya existe desde V7). Semilla de unos 12 tratamientos con códigos de la ficha:
   D0150 examen, D1110 profilaxis, D1330 higiene, D1351 sellante, D2140 amalgama, D2330/D2391 resinas, D2940
   curación, D3310/D3320/D3330 endodoncias, D7140 exodoncia. Precios provisionales en USD (la clínica dará los
   reales; se corrigen con una migración de datos).
2. `GET /api/v1/treatments?query=` (RECORD_READ): activos, ordenados por código. Sin `query` → todo el catálogo
   activo. Con `query` (mínimo 2 caracteres, si no → 400): código por prefijo **o** nombre con `ILIKE %query%`.
3. `POST /api/v1/encounters/{id}/treatments` (RECORD_CREATE) → 201:
   - `requireModifiableEncounter(id)`; el tratamiento debe existir y estar activo (si no → 400).
   - `quantity` opcional, por defecto 1, `@Min(1)`. `price` opcional; si no llega se copia `base_price`;
     `@DecimalMin("0.00")`.
   - `encounterDiagnosisId` opcional: debe existir, no estar anulado y pertenecer al **mismo paciente** (de esta
     atención o de una anterior). Si no → 409 `"El diagnóstico no pertenece a este paciente."`.
   - `toothCode` y `surfaceCode` opcionales; si llegan deben existir en `teeth` y `tooth_surfaces` (si no → 400).
   - `total = price × quantity` solo en la respuesta, nunca se guarda.
4. `GET /api/v1/encounters/{id}/treatments` (RECORD_READ): tratamientos no anulados de la atención (404 si la
   atención no existe).
5. `POST /api/v1/performed-treatments/{id}/void` (RECORD_UPDATE): `voidReason` `@NotBlank` (si no → 400);
   `requireModifiableEncounter` de su atención; si ya está anulado → 409. Guarda `voided_at` y `void_reason`.
   Nunca `DELETE`.
6. Se respetan las reglas de `architecture/SKILL.md`: `treatments` solo importa `ClinicalDirectory`,
   `DiagnosisSummary` y `EncounterSummary` de `clinical`. Dinero `decimal(10,2)` ↔ `BigDecimal`.

## Tests requeridos
**Integration** (`TreatmentFlowIT`, 3 casos; setup como `OdontogramFlowIT`: cita → check-in → atención →
diagnóstico, todo por API):
- `recordTreatment_withoutPrice_copiesBasePriceAndLinksDiagnosis`: `GET /treatments?query=resina` devuelve
  resultados; registrar uno con `quantity=2`, sin precio y con el diagnóstico de la atención → 201, `price =
  basePrice`, `total = 2 × basePrice`; `GET /encounters/{id}/treatments` lo lista.
- `voidTreatment_withoutReasonThenWithReason_excludesFromList`: sin motivo → 400; con motivo → 200 y ya no sale en
  el listado.
- `recordTreatment_diagnosisFromOtherPatient_returnsConflict`: diagnóstico de la atención de otro paciente → 409.

**Unit:** ninguno.

## Resultado
**Archivos creados:**
- `src/main/resources/db/migration/V9__treatments.sql`: `treatment_catalog` (BIGSERIAL, CHECK base_price >= 0, GIN trigram sobre `name`), `performed_treatments` (FKs a encounters, treatment_catalog, encounter_diagnoses, dental_findings, teeth, tooth_surfaces; CHECK quantity >= 1 y price >= 0; índice por encounter_id) y semilla de 12 tratamientos con precios provisionales en USD.
- `persistence/entity/Treatment.java`, `persistence/entity/PerformedTreatment.java` (esta última sin mapear `dental_finding_id`, que es de 010b).
- `persistence/repository/TreatmentRepository.java` (búsqueda nativa: código ILIKE prefijo o nombre ILIKE contiene), `persistence/repository/PerformedTreatmentRepository.java`.
- `clinical/DiagnosisSummary.java`.
- `treatments/api/TreatmentController.java`, `treatments/api/dto/` (TreatmentResponse, RecordTreatmentRequest, PerformedTreatmentResponse, VoidTreatmentRequest), `treatments/application/TreatmentService.java`.
- `src/test/java/com/bellidental/treatments/TreatmentFlowIT.java` (3 casos).

**Archivos modificados:**
- `clinical/ClinicalDirectory.java`: + `findDiagnosis(UUID)`.
- `clinical/application/ClinicalDirectoryImpl.java`: implementa `findDiagnosis` con `EncounterDiagnosisRepository` e `Icd10CodeRepository`.
- `docs/IMPLEMENTATION-PLAN.md`: §3 la fila 010 pasa a 010a/010b; §4.6 título y nota de `ChargeLedger` (pasa a la 012).

**Tests:**
- Desarrollo: `TreatmentFlowIT` 3/3 OK (failsafe-reports).
- Final `./mvnw -q clean verify` (EXIT=0), totales sumados de los reports:
  - Surefire: 83 tests, 0 failures, 0 errors, 0 skipped (17 clases).
  - Failsafe: 39 tests, 0 failures, 0 errors, 0 skipped (14 clases).

**Supuestos y problemas:**
- Diagnóstico inexistente → 400 (`InvalidInputException`, "Diagnóstico no existe"). Diagnóstico anulado → 409 ("El diagnóstico está anulado."). Diagnóstico de otro paciente → 409 con el mensaje exacto de la tarea. La tarea no separaba estos casos; lo tomé así.
- La búsqueda por código usa ILIKE (no distingue mayúsculas en el prefijo).
- `query` vacío o en blanco se trata como "sin query" (catálogo completo). Con 1 carácter → 400.
- Orden de validación en el void: "ya anulado" (409) antes que `requireModifiableEncounter`, igual que en `voidFinding` del odontograma.
- La IT usa la fecha `now + 70 días` y horas desde 08:00 para no solapar con otras ITs (las demás usan otros días).
- Sin commit. Fuera de la lista de archivos no toqué nada.


## Revisión
Estado: APROBADO

Cumple los criterios 1–6. `treatments` solo importa la raíz de `clinical`; V9 sigue el esquema, con CHECKs y GIN.
`./mvnw -q clean verify`: surefire 83, failsafe 39, sin fallos.
