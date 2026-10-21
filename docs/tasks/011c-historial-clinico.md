# 011c — Historial clínico del paciente (`clinical-summary`)

## Objetivo
Historial inmediato de §4.4 (RF06) y fuente de la impresión del 033 (RF09, pasa al MVP el 2026-10-19). Un solo
endpoint devuelve la cabecera de la historia clínica y la **línea de tiempo de sesiones** del paciente, con lo que
llena la sección 12 del 033: N.º de sesión, fecha, profesional y su código, diagnósticos, procedimientos y recetas.

No repite lo que ya dan otros endpoints: antecedentes y medicación (`GET /patients/{id}/clinical-record`),
odontograma vigente, índices y hallazgos pendientes (`GET /patients/{id}/odontogram`) y el detalle de una atención
(`GET /encounters/{id}`). El frontend los combina.

Módulo nuevo `clinicalsummary` (solo lectura): agrega `clinical`, `treatments` y `prescriptions`. No va en
`clinical` porque `treatments` y `prescriptions` ya dependen de `ClinicalDirectory` y se crearía un ciclo.

## Archivos a tocar
```
clinicalsummary/                                   (nuevo módulo)
  api/ClinicalSummaryController.java               GET /api/v1/patients/{id}/clinical-summary
  api/dto/ClinicalSummaryResponse.java             records de la respuesta (abajo)
  application/ClinicalSummaryService.java          arma la respuesta solo con Directories; sin HTTP
clinical/                                          (cambio mínimo)
  ClinicalDirectory.java                           + List<EncounterSummary> findPatientEncounters(UUID patientId)
                                                   + List<DiagnosisSummary> findDiagnoses(Collection<UUID> encounterIds)
  EncounterSummary.java                            + OffsetDateTime startAt (último componente)
  DiagnosisSummary.java                            + certainty, notes (últimos componentes)
  application/ClinicalDirectoryImpl.java           implementa y mapea (y cualquier sitio que construya los records)
treatments/
  TreatmentDirectory.java                          + List<PerformedTreatmentSummary> findPerformed(Collection<UUID> encounterIds)
  PerformedTreatmentSummary.java                   (nuevo) id, encounterId, treatmentCode, treatmentName,
                                                   toothCode, surfaceCode, quantity
  application/TreatmentDirectoryImpl.java          implementa
prescriptions/
  PrescriptionDirectory.java                       (nuevo) List<PrescriptionSummary> findIssued(Collection<UUID> encounterIds)
  PrescriptionSummary.java                         (nuevo) id, encounterId, prescribedAt, generalInstructions,
                                                   items: List<PrescriptionItemSummary>
  PrescriptionItemSummary.java                     (nuevo) medicationName, concentration, presentation, dose,
                                                   frequency, duration, quantity
  application/PrescriptionDirectoryImpl.java       (nuevo) implementa
clinicalrecords/
  ClinicalRecordSummary.java                       + LocalDate openedAt, UUID openedByProfessionalId
  ClinicalRecordDirectoryImpl.java                 lo mapea
professionals/
  ProfessionalDirectory.java                       + List<ProfessionalSummary> findByIds(Collection<UUID> ids)
  ProfessionalDirectoryImpl.java                   implementa
persistence/repository/
  EncounterRepository.java                         + atenciones no anuladas del paciente (vía appointments.patient_id),
                                                   ordenadas por start_at ASC
  EncounterDiagnosisRepository.java · PerformedTreatmentRepository.java · PrescriptionRepository.java ·
  PrescriptionItemRepository.java                  + consultas por colección de ids (`...In`), sin anulados
src/test/java/com/bellidental/clinicalsummary/ClinicalSummaryIT.java   (nuevo)
```

## Criterios de aceptación
1. `GET /api/v1/patients/{id}/clinical-summary` (`RECORD_READ`, en `ApiResponse`). Paciente inexistente → 404.
   La secretaria → 403 (no tiene `RECORD_READ`).
2. Respuesta (`data`):
   ```
   ClinicalSummaryResponse {
     patientId, clinicName,                      // clinicName = application.clinic.name (ESTABLECIMIENTO del 033)
     recordNumber, openedAt,                     // null si el paciente no tiene historia clínica
     openedBy: SessionProfessional | null,
     sessions: Session[]                         // [] si no hay atenciones
   }
   SessionProfessional { id, fullName, licenseNumber }
   Session {
     encounterId, sessionNumber, startAt, status,          // status OPEN/COMPLETED
     professional: SessionProfessional,
     diagnoses:     [{ id, code, description, certainty, notes }],
     treatments:    [{ id, code, name, toothCode, surfaceCode, quantity }],
     prescriptions: [{ id, prescribedAt, generalInstructions,
                       items[{ medicationName, concentration, presentation, dose, frequency, duration, quantity }] }]
   }
   ```
3. Sesiones: atenciones **no anuladas** del paciente (abiertas y completadas), ordenadas por `startAt` ascendente;
   `sessionNumber` = posición 1, 2, 3… (una atención anulada no ocupa número). Dentro de cada sesión: diagnósticos,
   tratamientos y recetas **no anulados**; recetas solo `ISSUED` (un borrador aún no es receta). Diagnósticos y
   tratamientos en orden de creación.
4. Rendimiento: número **constante** de consultas, sin N+1 (una por tipo de dato con `...In(encounterIds)`; los
   profesionales con `findByIds`). Responde < 1 s con 1000 pacientes.
5. Datos de otros módulos solo por sus `Directory` (raíz del paquete); `ClinicalSummaryService` no usa repositorios
   de otros módulos. Ningún módulo existente importa de `clinicalsummary`.
6. Los cambios en los records existentes no rompen a quienes los construyen (añadir el componente donde haga falta y
   declararlo en Resultado).

## Tests requeridos
**Integration** (`ClinicalSummaryIT`, 3 casos; setup por API como `PrescriptionFlowIT`, en días que no choquen con
otros IT):
- `getSummary_twoEncountersAndOneVoided_returnsNumberedSessionsWithDiagnosesTreatmentsAndIssuedPrescriptions`:
  paciente con 3 atenciones (la del medio anulada); la primera con diagnóstico, tratamiento con pieza y receta
  emitida, más una receta en borrador → 200, `recordNumber` y `clinicName` presentes, 2 sesiones numeradas 1 y 2
  en orden de fecha, la 1 con su diagnóstico (`certainty`), tratamiento (`code`, `toothCode`) y **una** receta (la
  emitida, con ítems), y `professional.licenseNumber`.
- `getSummary_patientWithoutEncounters_returnsEmptySessions`: paciente nuevo → 200, `sessions: []`,
  `recordNumber: null`.
- `getSummary_asSecretary_returnsForbidden`: → 403.

**Unit:** ninguno.

## Resultado
- **Archivos creados:**
  - `src/main/java/com/bellidental/clinicalsummary/api/ClinicalSummaryController.java`
  - `src/main/java/com/bellidental/clinicalsummary/api/dto/ClinicalSummaryResponse.java`
  - `src/main/java/com/bellidental/clinicalsummary/application/ClinicalSummaryService.java`
  - `src/main/java/com/bellidental/treatments/PerformedTreatmentSummary.java`
  - `src/main/java/com/bellidental/prescriptions/PrescriptionDirectory.java`
  - `src/main/java/com/bellidental/prescriptions/PrescriptionSummary.java`
  - `src/main/java/com/bellidental/prescriptions/PrescriptionItemSummary.java`
  - `src/main/java/com/bellidental/prescriptions/application/PrescriptionDirectoryImpl.java`
  - `src/test/java/com/bellidental/clinicalsummary/ClinicalSummaryIT.java`

- **Archivos modificados:**
  - `src/main/java/com/bellidental/clinical/ClinicalDirectory.java` (`+findPatientEncounters`, `+findDiagnoses`)
  - `src/main/java/com/bellidental/clinical/EncounterSummary.java` (`+OffsetDateTime startAt`)
  - `src/main/java/com/bellidental/clinical/DiagnosisSummary.java` (`+DiagnosisCertainty certainty`, `+String notes`)
  - `src/main/java/com/bellidental/clinical/application/ClinicalDirectoryImpl.java` (implementación de consultas batch y mapeos)
  - `src/main/java/com/bellidental/clinical/application/EncounterService.java` (adaptado constructor `EncounterSummary`)
  - `src/main/java/com/bellidental/treatments/TreatmentDirectory.java` (`+findPerformed`)
  - `src/main/java/com/bellidental/treatments/application/TreatmentDirectoryImpl.java` (implementación de consulta batch de tratamientos realizados)
  - `src/main/java/com/bellidental/clinicalrecords/ClinicalRecordSummary.java` (`+LocalDate openedAt`, `+UUID openedByProfessionalId`)
  - `src/main/java/com/bellidental/clinicalrecords/ClinicalRecordService.java` (adaptado constructor `ClinicalRecordSummary`)
  - `src/main/java/com/bellidental/professionals/ProfessionalDirectory.java` (`+findByIds`)
  - `src/main/java/com/bellidental/professionals/ProfessionalDirectoryImpl.java` (implementación de búsqueda por colección de ids)
  - `src/main/java/com/bellidental/persistence/repository/EncounterRepository.java` (`findActiveByPatientIdOrderByStartAtAsc`)
  - `src/main/java/com/bellidental/persistence/repository/EncounterDiagnosisRepository.java` (`findByEncounterIdInAndVoidedAtIsNullOrderByCreatedAtAsc`)
  - `src/main/java/com/bellidental/persistence/repository/PerformedTreatmentRepository.java` (`findByEncounterIdInAndVoidedAtIsNullOrderByCreatedAtAsc`)
  - `src/main/java/com/bellidental/persistence/repository/PrescriptionRepository.java` (`findByEncounterIdInAndStatusAndVoidedAtIsNullOrderByPrescribedAtAsc`)
  - `src/main/java/com/bellidental/persistence/repository/PrescriptionItemRepository.java` (`findByPrescriptionIdIn`)

- **Resultado de los tests:**
  - Surefire: Tests run: 92, Failures: 0, Errors: 0, Skipped: 0
  - Failsafe: Tests run: 61, Failures: 0, Errors: 0, Skipped: 0
  - Total: 153 tests exitosos (incluyendo los 3 casos de integración en `ClinicalSummaryIT`).

- **Correcciones aplicadas según Revisión:**
  1. `ProfessionalDirectoryImpl.findByIds`: eliminado el filtro de `deletedAt != null` para que profesionales dados de baja sigan apareciendo en el historial impreso del 033. Eliminado el respaldo `"Desconocido"` en `toSessionProfessional`.
  2. `ClinicalSummaryService`: dividido en métodos privados pequeños (`findDiagnosesByEncounter`, `findTreatmentsByEncounter`, `findPrescriptionsByEncounter`, `toMedicationItems`, `findProfessionalsById`, `buildSessions`, `toSession`, `toSessionProfessional`) y renombradas todas las abreviaturas (`profMap` -> `professionalsById`, `recordOpt` -> `clinicalRecord`, etc.).
  3. Limpieza menor: simplificado `ClinicalRecordService.toSummary` usando `record.getPatient().getId()` directamente; eliminadas líneas en blanco residuales al final de los archivos indicados.

## Revisión
Estado: APROBADO

Correcciones de la primera revisión, verificadas:
1. `findByIds` ya no filtra a los profesionales dados de baja, y se quitó el respaldo `"Desconocido"`. Una sesión
   antigua conserva el nombre y el `licenseNumber` de quien la atendió.
2. `getSummary` quedó corto y delega en métodos pequeños: por atención (`findDiagnosesByEncounter`,
   `findTreatmentsByEncounter`, `findPrescriptionsByEncounter`), `findProfessionalsById`, `buildSessions` y
   `toSession`. Ya no hay abreviaturas.
3. `ClinicalRecordService.toSummary` usa `getPatient().getId()` directo y no quedan líneas en blanco al final de
   los archivos.

Ya cumplía desde la primera revisión:
- módulo sin ciclos y solo con `Directory`;
- número constante de consultas;
- sesiones sin anuladas, numeradas por `startAt`;
- recetas solo `ISSUED`;
- `RECORD_READ`.

`./mvnw -q clean verify` corrido por Claude: surefire 92/0/0/0, failsafe 61/0/0/0.

Commit: incluir también `docs/CONTEXT.md`, `docs/IMPLEMENTATION-PLAN.md` y `docs/MVP-SCOPE.md` (RF09 en el MVP).
