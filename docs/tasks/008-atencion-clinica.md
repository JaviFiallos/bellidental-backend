# 008 — Atención clínica y diagnósticos

## Objetivo
Segunda parte del módulo `com.bellidental.clinical` (RF01, RF03; plan §4.4; D-6). El odontólogo abre la atención
sobre una cita con check-in, registra las secciones 1, 2, 4, 5, 7 y 10 del formulario 033, añade diagnósticos
CIE-10 y la completa, lo que deja la cita `ATTENDED`. Las correcciones se hacen por anulación.
Deja la interfaz pública `ClinicalDirectory`, que usarán odontograma (009), tratamientos (010) y recetas (011).

Fuera de alcance: `GET /patients/{id}/clinical-summary` (RF06). Necesita odontograma, tratamientos y recetas, así que
va en una tarea propia después de la 011. Tampoco entra Envers (tarea 013).

## Archivos a tocar
```
src/main/resources/db/migration/V7_1__encounters.sql                (nuevo)
shared/enums/EncounterStatus.java          (nuevo) OPEN, COMPLETED
shared/enums/DiagnosisCertainty.java       (nuevo) PRESUMPTIVE, DEFINITIVE
persistence/entity/Encounter.java, EncounterDiagnosis.java                      (nuevos)
persistence/repository/EncounterRepository.java, EncounterDiagnosisRepository.java (nuevos)
clinical/
  ClinicalDirectory.java         interfaz pública: Optional<EncounterSummary> findEncounter(UUID encounterId)
  EncounterSummary.java          record público: id, appointmentId, patientId, professionalId, status, closedAt, voided
  api/
    EncounterController.java
  api/dto/
    OpenEncounterRequest, UpdateEncounterRequest, AddDiagnosisRequest, VoidEncounterRequest,
    EncounterResponse, DiagnosisResponse
  application/
    EncounterService.java
    ClinicalDirectoryImpl.java
  domain/
    EncounterPolicy.java         reglas puras (abrir, modificar, completar); lanza BusinessException
src/test/java/com/bellidental/clinical/
  domain/EncounterPolicyTest, EncounterFlowIT
```
Depende de `AppointmentScheduler` (`findById`, `markAttended`), `ClinicalRecordDirectory` (`openIfAbsent`),
`CurrentUser` y `ClinicClock`. Usa `Icd10CodeRepository` del propio módulo. No importa `api`, `application` ni
`domain` de otros módulos.

## Criterios de aceptación
**Migración `V7_1__encounters.sql`** (columnas exactas de `docs/schema.dbml`, `encounters` y `encounter_diagnoses`)
1. `encounters`:
   - `encounter_id uuid DEFAULT gen_random_uuid()`;
   - `appointment_id` NOT NULL UNIQUE → `appointments`, `professional_id` → `professionals`;
   - `CHECK (status IN ('OPEN','COMPLETED'))`, `temperature decimal(4,1)`;
   - índice `(professional_id)`.
2. `encounter_diagnoses`:
   - FK `encounter_id` → `encounters` y `icd10_id` → `icd10_codes`;
   - `CHECK (certainty IN ('PRESUMPTIVE','DEFINITIVE'))`;
   - índice `(encounter_id)`;
   - índice único parcial `(encounter_id, icd10_id) WHERE voided_at IS NULL`.
3. Sin semillas. No tocar V1–V7.

**Entidades**: ids de otras tablas como columnas simples (como en `Appointment`, sin `@ManyToOne`); `status` y
`certainty` con `@Enumerated(STRING)`; fechas `OffsetDateTime`; `temperature` `BigDecimal`; auditoría como `Patient`.

**Reglas (`domain/EncounterPolicy`, Java puro; toda violación → `BusinessException`, 409)**
4. `checkCanOpen(AppointmentStatus status, OffsetDateTime checkedInAt, boolean encounterExists)`:
   - la cita debe estar `PENDING` o `CONFIRMED`;
   - debe tener check-in;
   - no puede tener ya una atención.
5. `checkCanModify(UUID attendingProfessionalId, UUID currentProfessionalId, boolean voided)`:
   - la atención no puede estar anulada;
   - solo el odontólogo que atendió la modifica.
   Aplica a editar, añadir o anular diagnósticos, completar y anular la atención. También aplica con la atención
   `COMPLETED`, porque D-6 permite corregir después del cierre.
6. `checkCanComplete(EncounterStatus status, long activeDiagnoses)`: debe estar `OPEN` y tener ≥ 1 diagnóstico no
   anulado.

**Endpoints** — `RECORD_CREATE` para abrir, `RECORD_UPDATE` para escribir, `RECORD_READ` para leer. La secretaria no
tiene `RECORD_*`, así que recibe 403 sin código extra.
7. `POST /api/v1/appointments/{appointmentId}/encounter` (201). `OpenEncounterRequest`: `chiefComplaint`
   (`@NotBlank`), obligatorio porque la columna es NOT NULL. En este orden:
   1. usuario sin profesional (`CurrentUser.professionalId()` vacío) → 409;
   2. cita inexistente (`AppointmentScheduler.findById`) → 404;
   3. `checkCanOpen` (usa `EncounterRepository.existsByAppointmentId`);
   4. crear con `professional_id` = profesional actual (puede diferir del agendado), `start_at` = `ClinicClock.now()`,
      `status=OPEN`;
   5. `ClinicalRecordDirectory.openIfAbsent(patientId, professionalId)`.
8. `PUT /api/v1/encounters/{id}`. `UpdateEncounterRequest` reemplaza la cabecera completa:
   - `chiefComplaint` (`@NotBlank`), `currentIllness`;
   - signos vitales opcionales: `bpSystolic` 50–260, `bpDiastolic` 30–160, `heartRate` 30–220,
     `temperature` 34.0–43.0, `respiratoryRate` 5–60;
   - `stomatognathicExam`;
   - `periodontalDisease` y `fluorosis` ∈ `LEVE|MODERADA|SEVERA`, `malocclusion` ∈ `ANGLE_I|ANGLE_II|ANGLE_III`
     (`@Pattern`, opcionales; valores de `docs/CONTEXT.md` §7);
   - `diagnosticPlan`, `therapeuticPlan`, `educationalPlan`.
   Aplica `checkCanModify`. Atención inexistente → 404.
9. `POST /api/v1/encounters/{id}/diagnoses` (201). `AddDiagnosisRequest`: `icd10Id`, `certainty` (obligatorios),
   `notes`.
   - código CIE-10 inexistente o inactivo → 404;
   - aplica `checkCanModify`;
   - mismo `icd10Id` ya activo en la atención → 409 (`existsByEncounterIdAndIcd10IdAndVoidedAtIsNull`);
   - con la atención `COMPLETED` se permite, como corrección (D-6).
10. `POST /api/v1/encounter-diagnoses/{id}/void`:
    - diagnóstico inexistente → 404;
    - ya anulado → 409;
    - aplica `checkCanModify` sobre su atención;
    - fija `voided_at = now`.
11. `POST /api/v1/encounters/{id}/complete`:
    - aplica `checkCanModify` y `checkCanComplete`;
    - `status=COMPLETED`, `end_at = closed_at = now`;
    - `AppointmentScheduler.markAttended(appointmentId)` en la misma transacción.
12. `POST /api/v1/encounters/{id}/void`. `VoidEncounterRequest`: `voidReason` (`@NotBlank`).
    - aplica `checkCanModify` (anulada → 409);
    - fija `voided_at` y `void_reason`;
    - no cambia el estado de la cita.
13. `GET /api/v1/encounters/{id}` → `EncounterResponse`:
    - id, appointmentId, patientId, professionalId, status, startAt, endAt, closedAt, todos los campos de cabecera,
      voidedAt, voidReason;
    - `diagnoses`: todos, incluidos los anulados, con `voidedAt`. Cada `DiagnosisResponse` lleva id, icd10Id, code,
      description, certainty, notes, createdAt, voidedAt;
    - `patientId` se obtiene de la cita vía `AppointmentScheduler`.
14. `ClinicalDirectory.findEncounter(UUID)` → `EncounterSummary` (`voided` = `voidedAt != null`). Lo implementa
    `ClinicalDirectoryImpl` delegando en el servicio, sin reglas propias.

**General**
15. Errores con `GlobalExceptionHandler`. No se tocan otros módulos ni migraciones anteriores.

## Tests requeridos
- Unit `domain/EncounterPolicyTest` (4 casos):
  - abrir sin check-in → `BusinessException`;
  - modificar siendo otro profesional → `BusinessException`;
  - completar sin diagnósticos activos → `BusinessException`;
  - completar ya `COMPLETED` → `BusinessException`.
- Integration `EncounterFlowIT` (`TestcontainersConfiguration`, login por HTTP). En el setup:
  - la secretaria crea un paciente;
  - la odontóloga crea una cita para mañana (origen `PROFESSIONAL`, sin horario) y le hace check-in.

  Casos:
  1. la odontóloga abre la atención → 201; completar sin diagnóstico → 409; añade `K02.1` (id obtenido por
     `GET /icd10?query=K02.1`) → 201; completar → 200; `GET /appointments/{id}` → `ATTENDED`;
     `GET /patients/{id}/clinical-record` → 200 (la historia se abrió sola);
  2. la secretaria intenta abrir la atención de otra cita con check-in → 403.

## Resultado
Implementado por Haiku 5.5 (obrero), a petición del usuario. El código ya existía sin documentar; esta sección lo describe a partir del diff y de la verificación.

**Archivos creados (sin commit):**
- Migración: `src/main/resources/db/migration/V7_1__encounters.sql` (`encounters`, `encounter_diagnoses`, índices, índice único parcial de diagnósticos activos).
- Enums: `shared/enums/EncounterStatus.java`, `shared/enums/DiagnosisCertainty.java`.
- Entidades y repositorios: `persistence/entity/Encounter.java`, `EncounterDiagnosis.java`; `persistence/repository/EncounterRepository.java`, `EncounterDiagnosisRepository.java`.
- Módulo `clinical/`: `ClinicalDirectory.java`, `EncounterSummary.java`, `api/EncounterController.java`, `api/dto/` (`OpenEncounterRequest`, `UpdateEncounterRequest`, `AddDiagnosisRequest`, `VoidEncounterRequest`, `EncounterResponse`, `DiagnosisResponse`), `application/EncounterService.java`, `application/ClinicalDirectoryImpl.java`, `domain/EncounterPolicy.java`.
- Tests: `src/test/java/com/bellidental/clinical/domain/EncounterPolicyTest.java` (4), `src/test/java/com/bellidental/clinical/EncounterFlowIT.java` (2).

**Correcciones de Claude/obrero durante esta revisión:**
- `EncounterFlowIT` fallaba en `setUp` con `SQLState 23P01` (violación de la restricción de cruces): cada test creaba una cita para mañana a las 10:00 en la misma base compartida. Ahora `setUp` solo hace login, paciente y lookup de profesional/especialidad; cada test crea su propia cita con `createCheckedInAppointment(day)`, en días distintos (+1 y +2).

**Cumplimiento frente al plan:**
- Reglas en `EncounterPolicy` (`checkCanOpen`, `checkCanModify`, `checkCanComplete`), en el orden de la tarea.
- Abrir: solo profesional, cita PENDING/CONFIRMED con check-in, sin atención previa; `professional_id` del usuario actual; `openIfAbsent` de la historia clínica.
- Editar cabecera: validaciones con `@Min/@Max/@DecimalMin/@Pattern` según la tarea; reemplaza la cabecera completa.
- Diagnósticos: 404 si el código no existe o está inactivo, 409 si ya hay uno activo igual; se permite con la atención COMPLETED (corrección, D-6).
- Anular diagnóstico y anular atención: 409 si ya están anulados; anular atención no cambia la cita.
- Completar: exige ≥1 diagnóstico activo; fija `status`, `end_at` y `closed_at`; llama a `markAttended` de la cita.
- Lectura `GET /api/v1/encounters/{id}`: incluye diagnósticos anulados con `voidedAt`.
- `ClinicalDirectory.findEncounter` delega en el servicio, sin reglas propias.

**Resultado de los tests (`./mvnw clean verify`, EXIT 0):**
- Surefire: 79 tests, 0 fallos, 0 errores (incluye `EncounterPolicyTest` 4).
- Failsafe: 21 tests, 0 fallos, 0 errores (incluye `EncounterFlowIT` 2).

**Problemas y supuestos:**
1. `chief_complaint` es obligatorio al abrir (NOT NULL en el esquema), por eso `OpenEncounterRequest` exige el motivo.
2. Los valores de `periodontal_disease`, `fluorosis` y `malocclusion` se validan en la API, no con CHECK en la base; el esquema no los restringe.
3. `EncounterService.toSummary` es público porque lo usa `ClinicalDirectoryImpl` dentro del mismo módulo.
4. Los cambios no están commiteados ni documentados en el resto de la tarea hasta esta sección.
5. Se modificó `EncounterService.java` para utilizar `saveAndFlush` al abrir la atención y añadir diagnósticos, capturando la `DataIntegrityViolationException` y lanzando `BusinessException` (409) para gestionar las condiciones de carrera en base a los nombres de las restricciones (como en la tarea 006a).
6. Se ajustó la obtención de `patientId` en `EncounterService` y `ClinicalDirectoryImpl` para que lance `ResourceNotFoundException` en lugar de devolver `null` silenciosamente en caso de que `AppointmentScheduler.findById` no encuentre la cita.

## Revisión
**Estado: CORRECCIONES** (revisión 1, Claude)

Verificado con `./mvnw clean verify`: surefire 79/79, failsafe 21/21 (`EncounterFlowIT` 2, `EncounterPolicyTest` 4).
Cumplidos y revisados en el código: migración V7_1 (columnas, CHECK, índice único parcial); orden de validaciones de
apertura (profesional → cita 404 → `checkCanOpen` → alta → `openIfAbsent`); `checkCanModify` en todas las escrituras;
diagnósticos (404 por código inactivo, 409 duplicado, permitido con `COMPLETED`); completar (≥1 diagnóstico, `markAttended`
en la misma transacción); anulaciones (409 si ya anulado); `GET` con diagnósticos anulados; permisos `hasAuthority`;
`ClinicalDirectory` sin reglas propias; la IT corregida crea una cita por test.

Corregir:
1. **Condiciones de carrera devuelven 500 en lugar de 409.** `open` comprueba `existsByAppointmentId` y después inserta;
   si dos peticiones llegan a la vez, la segunda choca con el `UNIQUE` de `encounters.appointment_id` y
   `DataIntegrityViolationException` cae en el manejador genérico (500). Lo mismo con `addDiagnosis` y el índice parcial
   `idx_encounter_diagnoses_active`. Como en `AppointmentService.saveOrTranslateOverlap` (006a): traducir la violación
   de esas dos restricciones a `BusinessException` (409), buscando el nombre de la restricción en la cadena de causas
   (`ConstraintViolationException.getConstraintName()`). Sin tests nuevos.
2. **`patientId` nulo en silencio.** `getPatientId` devuelve `null` si `AppointmentScheduler.findById` no encuentra la
   cita, y la respuesta sale con `patientId: null`. Como la cita de una atención no puede desaparecer (FK), lanzar
   `ResourceNotFoundException` en ese caso, o bien documentar que es imposible. Elegir una de las dos y reflejarla en Resultado.

No hace falta cambiar nada más. Tras aplicar las correcciones, reejecutar `./mvnw clean verify` y actualizar Resultado con los números reales.

---
**Estado: APROBADO** (revisión 2, Claude)

Verificado con `./mvnw clean verify`: surefire 79/79, failsafe 21/21.

Resueltos los 2 puntos:
1. **Carreras:** `open` y `addDiagnosis` usan `saveAndFlush` y traducen las violaciones de `encounters_appointment_id_key`
   e `idx_encounter_diagnoses_active` a 409.
2. **`patientId`:** si la cita no aparece, se lanza `ResourceNotFoundException` en el servicio y en `ClinicalDirectoryImpl`.

Correcciones mínimas de Claude:
- **Nombres completos:** se quitaron los de `org.hibernate...ConstraintViolationException` (`EncounterService`) y
  `com.bellidental.shared.exception.ResourceNotFoundException` (`ClinicalDirectoryImpl`); ahora son `import`.
- **Respaldo de la apertura:** se quitó `isConstraintViolation(ex, "appointment_id")`. Con la búsqueda en el mensaje,
  cualquier error que mencionara esa columna (por ejemplo una FK) se habría convertido en "ya tiene una atención".

Reverificado tras las correcciones: surefire 79/79, failsafe 21/21.
