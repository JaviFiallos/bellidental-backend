# 011a — Recetas: medicamentos, presets y ciclo DRAFT → ISSUED (V10)

## Objetivo
Módulo nuevo `com.bellidental.prescriptions` (RF04, RF05, plan §4.7). El odontólogo busca medicamentos desde la
primera letra, con sus posologías predefinidas (presets), y arma una receta en la atención, ligada a un diagnóstico
de esa misma atención. La receta en DRAFT se edita; al emitirla (ISSUED) queda inmutable, y corregirla es anularla y
crear otra. La 011 se divide en dos: aquí va todo menos el PDF, que va en la 011b.

Misma regla de modificación que en el resto del módulo clínico (D-6): `ClinicalDirectory.requireModifiableEncounter`.
Para validar el diagnóstico se usa `ClinicalDirectory.findDiagnosis`, que añade la 010a.

Decisión (D-16, se anota en el plan): reemplazar los ítems de una receta **DRAFT** borra sus filas de
`prescription_items`. Es la única excepción a «sin `DELETE` en datos clínicos», porque un borrador nunca se entregó
al paciente. Los ítems de una receta ISSUED nunca se borran.

## Archivos a tocar
```
src/main/resources/db/migration/V10__prescriptions.sql      (nuevo)
shared/enums/PrescriptionStatus.java                        (nuevo) DRAFT, ISSUED
persistence/entity/
  Medication.java, MedicationPreset.java, Prescription.java, PrescriptionItem.java      (nuevos)
persistence/repository/
  MedicationRepository.java, MedicationPresetRepository.java,
  PrescriptionRepository.java, PrescriptionItemRepository.java                           (nuevos)
prescriptions/                                              (nuevo)
  api/MedicationController.java
  api/PrescriptionController.java
  api/dto/
    MedicationResponse.java          id, name, presentation, concentration, route, presets
    MedicationPresetResponse.java    id, label, dose, frequency, duration, instructions
    PrescriptionRequest.java         encounterDiagnosisId, generalInstructions, items (@Valid, @NotEmpty)
    PrescriptionItemRequest.java     medicationId, medicationPresetId, dose, frequency, duration, quantity, instructions
    PrescriptionResponse.java        id, encounterId, encounterDiagnosisId, icd10Code, icd10Description, status,
                                     prescribedAt, generalInstructions, voided, items
    PrescriptionItemResponse.java    id, medicationId, medicationName, presentation, concentration,
                                     medicationPresetId, dose, frequency, duration, quantity, instructions
  application/
    MedicationService.java
    PrescriptionService.java
  domain/
    PrescriptionRules.java           reglas puras: checkEditable, checkCanIssue, resolveItemField
src/test/java/com/bellidental/prescriptions/
  domain/PrescriptionRulesTest.java  (nuevo)
  PrescriptionFlowIT.java            (nuevo)
docs/IMPLEMENTATION-PLAN.md          tabla §3: filas 011a/011b; §6: D-16
```

## Criterios de aceptación
1. **V10** según `docs/schema.dbml` §9 (`medications`, `medication_presets`, `prescriptions`, `prescription_items`,
   FKs de las `Ref`), más `CHECK (status IN ('DRAFT','ISSUED'))` e índice GIN trigram sobre `medications.name`.
   Semilla: amoxicilina 500 mg, ibuprofeno 400 mg, paracetamol 500 mg, clindamicina 300 mg, ketorolaco 10 mg,
   clorhexidina 0,12 % (enjuague) y 2–4 más de uso odontológico, cada uno con 1–2 presets (p. ej. amoxicilina
   «Cada 8 h × 7 días»: dose `1 cápsula`, frequency `cada 8 horas`, duration `7 días`).
2. `GET /api/v1/medications?query=` (RECORD_READ): activos, con sus presets, máximo 20. `query` obligatorio de 1+
   caracteres (vacío → 400). Coincide por prefijo de nombre (`ILIKE query%`); con 3+ caracteres también por
   similitud trigram (`name % :query`). Los de prefijo van primero, luego por nombre. Consulta nativa en
   `MedicationRepository`.
3. `POST /api/v1/encounters/{id}/prescriptions` (RECORD_CREATE) → 201, DRAFT:
   - `requireModifiableEncounter(id)`.
   - `encounterDiagnosisId` obligatorio; debe existir, no estar anulado y ser **de esta atención**. Si no → 409
     `"El diagnóstico no pertenece a esta atención."`.
   - Cada ítem: el medicamento debe existir y estar activo (si no → 400). Con `medicationPresetId`, el preset debe
     ser de ese medicamento (si no → 400), y `dose`, `frequency`, `duration` e `instructions` que no lleguen se
     copian del preset; lo que llega gana. Sin preset, `dose`, `frequency` y `duration` son obligatorios (si no →
     400). `quantity` es texto libre opcional («21 tabletas»).
4. `PUT /api/v1/prescriptions/{id}` (RECORD_UPDATE): mismo cuerpo; reemplaza diagnóstico, indicaciones e ítems.
   Solo DRAFT no anulada; si no → 409 `"Solo se puede modificar una receta en borrador."`.
5. `POST /api/v1/prescriptions/{id}/issue` (RECORD_UPDATE): DRAFT no anulada y con al menos un ítem → ISSUED,
   `prescribed_at = now`. Si no → 409.
6. `POST /api/v1/prescriptions/{id}/void` (RECORD_UPDATE): DRAFT o ISSUED no anulada → `voided_at = now`; ya
   anulada → 409.
7. `GET /api/v1/prescriptions/{id}` y `GET /api/v1/encounters/{id}/prescriptions` (RECORD_READ). El listado
   excluye las anuladas. La respuesta trae código y descripción CIE-10 del diagnóstico (vía `findDiagnosis`).
8. Las comprobaciones de estado y la copia del preset viven en `domain/PrescriptionRules` (sin Spring); el servicio
   solo las llama. Todas las operaciones que modifican pasan por `requireModifiableEncounter`.

## Tests requeridos
**Unit** (`PrescriptionRulesTest`, 4 casos):
- `resolveItemField_requestNull_copiesPresetValue`
- `resolveItemField_requestValue_overridesPreset`
- `checkCanIssue_draftWithItems_passes`
- `checkEditable_issued_throwsBusinessException`

**Integration** (`PrescriptionFlowIT`, 3 casos; setup como `OdontogramFlowIT`):
- `createAndIssue_withPreset_copiesPosologyAndBecomesIssued`: `GET /medications?query=amo` trae amoxicilina con
  presets → crear receta con ese preset sin dose/frequency/duration → 201 con los valores del preset → issue →
  ISSUED.
- `updatePrescription_whenIssued_returnsConflict`: `PUT` sobre una receta ISSUED → 409.
- `createPrescription_diagnosisFromOtherEncounter_returnsConflict`: diagnóstico de otra atención → 409.

## Resultado
**Archivos creados:**
- `src/main/resources/db/migration/V10__prescriptions.sql`: tablas `medications`, `medication_presets`, `prescriptions`, `prescription_items`; índice único `(name, concentration, presentation)`; GIN trigram sobre `medications.name`; `CHECK` de estado; semilla de 8 medicamentos y 9 presets (amoxicilina con «Cada 8 h × 7 días»).
- `shared/enums/PrescriptionStatus.java`.
- `persistence/entity/`: `Medication`, `MedicationPreset`, `Prescription`, `PrescriptionItem`.
- `persistence/repository/`: `MedicationRepository` (consulta nativa `searchActive`: prefijo ILIKE + trigram con 3+ caracteres, máx. 20), `MedicationPresetRepository`, `PrescriptionRepository`, `PrescriptionItemRepository`.
- `prescriptions/api/`: `MedicationController`, `PrescriptionController`; `api/dto/`: 8 records según la tarea.
- `prescriptions/application/`: `MedicationService`, `PrescriptionService`.
- `prescriptions/domain/PrescriptionRules.java`: `checkEditable`, `checkCanIssue`, `checkCanVoid`, `resolveItemField`.
- Tests: `src/test/java/com/bellidental/prescriptions/domain/PrescriptionRulesTest.java` (4) y `src/test/java/com/bellidental/prescriptions/PrescriptionFlowIT.java` (3).

**Archivo modificado:** `docs/IMPLEMENTATION-PLAN.md`: fila 011 dividida en 011a/011b en §3 y decisión D-16 en §6.

**Tests (`./mvnw -q clean verify`):**
- Surefire: 89 run, 0 failures, 0 errors (incluye los 4 de `PrescriptionRulesTest`).
- Failsafe: 44 run, 1 failure, 0 errors. El fallo es `AvailabilityIT.walkIn_noAppointmentsToday_createsWalkInCheckedIn` (409 en vez de 201), el flake conocido de 23:30 a 24:00; la ejecución fue a las 23:46. `PrescriptionFlowIT` pasa 3/3.
- El build termina con EXIT=1 solo por ese flake.

**Supuestos y problemas:**
- `prescribed_at` (NOT NULL en el esquema) se fija al crear y se actualiza al emitir.
- `resolveItemField` trata un valor en blanco como ausente: copia el del preset.
- `issue` y `void` devuelven `PrescriptionResponse`; la tarea no define el cuerpo de respuesta. `void` no pide motivo, porque la tarea no lo menciona.
- `GET /medications` sin `query` o con `query` en blanco devuelve 400 (`InvalidInputException`), no 500.
- `prescription_items` no tiene columna de posición en `schema.dbml`, así que los ítems no tienen orden garantizado al leerlos.
- Los servicios usan `ClinicalDirectory.requireModifiableEncounter` en crear, editar, emitir y anular, y `findDiagnosis` para validar el diagnóstico y para el CIE-10 de la respuesta.
- El `.gitkeep` de `prescriptions/` se dejó tal cual.
- No se hizo commit.

## Revisión
Estado: APROBADO

Cumple los criterios 1–8: reglas en `domain/PrescriptionRules`, diagnóstico validado contra la atención, borrado de
ítems solo en DRAFT (D-16 anotado), búsqueda nativa por prefijo + trigram. `./mvnw -q clean verify`: surefire 89/0,
failsafe 44 con 1 fallo ajeno (`AvailabilityIT.walkIn_noAppointmentsToday`, corrida a las 23:47; ver 010b).
Nota para la 011b: `prescription_items` no tiene columna de orden; el PDF numera los ítems en el orden en que se
leen.
