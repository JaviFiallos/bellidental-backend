# 005 — Historia clínica y antecedentes

## Objetivo
Crear el módulo `com.bellidental.clinicalrecords` (RF01 parcial; plan §4.2): una historia clínica por paciente,
catálogo de los 10 antecedentes del 033 y antecedentes personales/familiares registrados por el odontólogo,
corregibles solo por anulación. Deja una interfaz pública para que la atención clínica (tarea 008) abra la
historia automáticamente.

## Archivos a tocar
- `src/main/resources/db/migration/V5__clinical_records.sql` (nuevo).
- `persistence/entity/`: `ClinicalRecord`, `MedicalHistoryCondition`, `MedicalHistoryEntry` (nuevos).
- `persistence/repository/`: `ClinicalRecordRepository`, `MedicalHistoryConditionRepository`,
  `MedicalHistoryEntryRepository` (nuevos).
- `clinicalrecords/` (nuevo paquete): `ClinicalRecordController`, `ClinicalRecordService`, DTOs,
  `ClinicalRecordDirectory` (interfaz pública) y su implementación, `HistoryScope` (enum `PERSONAL`, `FAMILY`).
- Tests unitarios en `src/test/java/com/bellidental/clinicalrecords/`.

## Criterios de aceptación
**Migración V5** (exactamente las columnas de `docs/schema.dbml`, sección 4)
1. Tablas `clinical_records`, `medical_history_conditions`, `medical_history_entries` con sus FK
   (`patient_id` UNIQUE → `patients`, `opened_by` → `professionals`, entradas → historia y condición).
   `CHECK (scope IN ('PERSONAL', 'FAMILY'))`. Índice en `medical_history_entries(clinical_record_id)`.
2. Secuencia `clinical_record_number_seq` para el número de historia.
3. Semilla de `medical_history_conditions` con ids fijos 1–10 en este orden (`display_order` = id), dejando la
   secuencia/identity ajustada como en V4: Alergia a antibióticos, Alergia a anestesia, Hemorragias, VIH/SIDA,
   Tuberculosis, Asma, Diabetes, Hipertensión, Enfermedad cardíaca, Otro.

**Entidades**: `created_at`/`updated_at`/`recorded_at`/`voided_at` como `OffsetDateTime` (igual que `Patient`);
`opened_at` es `date` → `LocalDate`. Sin `DELETE` sobre ninguna de las tres tablas.

**Historia clínica**
4. `POST /api/v1/patients/{patientId}/clinical-record` (RECORD_CREATE) abre la historia:
   - paciente inexistente → 404 (usar `PatientDirectory`, no `PatientRepository`);
   - ya tiene historia → 409;
   - el usuario actual debe ser profesional (`CurrentUser.professionalId()`); si no → 409;
   - `opened_by` = ese profesional, `opened_at` = hoy en `application.clinic.zone`,
     `record_number` = `HC-` + `nextval` con 6 dígitos (`HC-000001`).
5. `GET /api/v1/patients/{patientId}/clinical-record` (RECORD_READ): datos de la historia + antecedentes no anulados
   (con nombre de la condición, ordenados por `display_order`); `?includeVoided=true` incluye los anulados.
   Sin historia → 404.
6. `PUT /api/v1/clinical-records/{id}` (RECORD_UPDATE): `currentMedication` y `noKnownHistory`.
   `noKnownHistory=true` con antecedentes no anulados → 409.
7. `ClinicalRecordDirectory` (interfaz pública): `findByPatientId(UUID)` y
   `openIfAbsent(UUID patientId, UUID professionalId)` (idempotente: devuelve la existente o la crea con las reglas
   del punto 4). Devuelve un record propio del módulo, no la entidad.

**Antecedentes**
8. `GET /api/v1/medical-history-conditions` (RECORD_READ): condiciones activas por `display_order`.
9. `POST /api/v1/clinical-records/{id}/history-entries` (RECORD_UPDATE): `conditionId`, `scope`, `relative`,
   `description`.
   - condición inexistente o inactiva → 404; historia inexistente → 404;
   - `FAMILY` exige `relative` (máx. 30); `PERSONAL` lo prohíbe → 400;
   - condición "Otro" (id 10, constante en el servicio) exige `description` → 400;
   - al crear, la historia pasa a `noKnownHistory=false`.
10. `POST /api/v1/history-entries/{id}/void` (RECORD_UPDATE): fija `voided_at=now` y `active=false`.
    Ya anulado → 409. Corregir = anular y crear otro (no hay PUT de antecedentes).

**General**
11. Permisos con `hasAuthority`. La SECRETARIA no tiene `RECORD_*` → 403 en todos estos endpoints (no hace falta código extra).
12. Errores con `GlobalExceptionHandler`: 400 `InvalidInputException`, 404 `ResourceNotFoundException`,
    409 `BusinessException`. No se toca `patients`, `professionals` ni migraciones anteriores.

## Tests requeridos
- Unit `ClinicalRecordServiceTest`:
  - `open`: paciente inexistente → 404; historia existente → conflicto; usuario sin profesional → conflicto;
    número formateado `HC-000001` y `opened_at` en la zona configurada.
  - `openIfAbsent`: con historia existente no crea otra.
  - `addEntry`: FAMILY sin `relative` → 400; PERSONAL con `relative` → 400; "Otro" sin descripción → 400;
    entrada válida pone `noKnownHistory=false`.
  - `update`: `noKnownHistory=true` con entradas activas → conflicto.
  - `voidEntry`: fija `voidedAt` y `active=false`; ya anulada → conflicto.
- Integration: ninguno (lo cubre `EncounterFlowIT` en la tarea 008). La migración V5 debe pasar con los `*IT` existentes.

## Resultado
**Archivos modificados:**
- `V5__clinical_records.sql`: Migración con creación de tablas, índices y secuencias.
- `ClinicalRecord`, `MedicalHistoryCondition`, `MedicalHistoryEntry`: Entidades con uso de `OffsetDateTime`.
- `ClinicalRecordRepository`, `MedicalHistoryConditionRepository`, `MedicalHistoryEntryRepository`: Repositorios JPA.
- `ClinicalRecordService`, `ClinicalRecordController`, `ClinicalRecordDirectoryImpl`: Lógica de negocio, controlador REST y servicio interno.
- `ClinicalRecordSummary`, `ClinicalRecordResponse`, `MedicalHistoryConditionResponse`, `MedicalHistoryEntryResponse`, `CreateMedicalHistoryEntryRequest`, `UpdateClinicalRecordRequest`, `HistoryScope`: DTOs y enumeraciones del módulo.
- `ClinicalRecordServiceTest`: Pruebas unitarias actualizadas con mocks de repositorios.

**Resultado de los tests:**
- Surefire: 42 tests ejecutados, 0 fallos, 0 errores.
- Failsafe: 11 tests ejecutados, 0 fallos, 0 errores.

**Soluciones implementadas:**
- La historia clínica se crea generando el correlativo `HC-XXXXXX` tomando la fecha en la zona configurada de `application.clinic.zone`.
- Se validó el alcance de antecedentes médicos; la opción `FAMILY` obliga a especificar el familiar, la condición `Otro` exige descripción y al crear un antecedente la bandera `noKnownHistory` cambia a `false`.
- Los endpoints REST están protegidos por roles con sus debidos `@PreAuthorize`.
- Se corrigieron duplicidades en el servicio, extrayendo las funciones de formateo, se usó getReferenceById para evitar construir entidades artificialmente y se delega el ordenamiento a los queries del repositorio.

## Revisión
**Estado: CORRECCIONES** (revisión 1, Claude)

Verificado con `./mvnw clean verify`: surefire 42/42 (`ClinicalRecordServiceTest` 12), failsafe 11/11; V5 migra bien.
Cumplidos: migración y semilla según `schema.dbml`, secuencia `HC-000001`, reglas de apertura, scope/relative,
"Otro", `noKnownHistory`, anulación, permisos `hasAuthority`, `ClinicalRecordDirectory` con record propio,
uso de `PatientDirectory`. Corregir:
1. **Duplicación en `ClinicalRecordService`:** `getByPatientId` y `update` repiten ordenar y mapear las entradas.
   Ordenar en el repositorio (`findByClinicalRecordIdAndActiveTrueOrderByConditionDisplayOrderAsc` y su variante
   sin `ActiveTrue`) y extraer un único `toResponse(ClinicalRecord record, List<MedicalHistoryEntry> entries)`.
   Quitar el comentario `// order by condition display order`. Lo mismo para el `ClinicalRecordSummary`, que se
   construye en tres sitios: un único `toSummary(ClinicalRecord)`.
2. **Referencias a entidades:** no construir `Patient.builder().id(...)` ni `Professional.builder().id(...)`; usar
   `getReferenceById` de `PatientRepository` y `ProfessionalRepository` (capa `persistence`, compartida).
3. **Nombres:** `r` → `record` / `clinicalRecord`; `nextVal` → `recordSequenceValue`.
4. **Archivo suelto:** borrar `compile_debug.log` de la raíz (no debe entrar al commit).
5. **Resultado:** reemplazarlo con los números reales de esta ejecución (no 38/12) y sin la frase
   "los antecedentes médicos inician inactivos", que no describe el código.

---
**Estado: APROBADO** (revisión 2, Claude)

Verificado con `./mvnw clean verify`: surefire 42/42, failsafe 11/11. Resueltos los 5 puntos: orden en el
repositorio con `toResponse`/`toSummary` únicos, `getReferenceById`, nombres claros, `compile_debug.log` borrado,
Resultado con números reales.
Corrección mínima de Claude (nueva regla `architecture/SKILL.md`): `HistoryScope` movido a `shared/enums` para que
`persistence` no dependa de `clinicalrecords`. Reverificado: surefire 42/42, failsafe 11/11.
