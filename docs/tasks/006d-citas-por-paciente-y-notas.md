# 006d — Citas por paciente y observaciones administrativas (G-4 y G-5 del frontend)

> Corrección pequeña sobre la 006 (agenda) y la 004 (pacientes). Son dos huecos que bloquean la misma pantalla del
> frontend: la ficha de recepción del paciente (tarea 005 del frontend). Por eso van juntos, aunque tocan dos
> módulos.

## Objetivo
La ficha de recepción muestra la próxima cita del paciente, sus citas anteriores y una línea de "Observaciones"
(por ejemplo, "presupuesto ortodoncia $1.200, abonos mensuales").
- **G-4:** `GET /appointments` exige `from` y `to` y no filtra por paciente, así que no hay forma de listar las citas
  de un paciente.
- **G-5:** `admin_notes` existe en la tabla (V3) y en `UpdatePatientRequest`, pero `PatientResponse` no la devuelve
  y `CreatePatientRequest` no la acepta. Se puede escribir, pero no leer.

Sin migraciones: la columna `patients.admin_notes text` ya existe.

## Archivos a tocar
```
appointments/
  api/AppointmentController.java          GET /appointments: + patientId opcional; from/to pasan a opcionales
  application/AppointmentService.java     getAppointments(professionalId, patientId, from, to): reglas de abajo
persistence/repository/
  AppointmentRepository.java              + findByPatientIdOrderByStartAtAsc(patientId)
                                          + findByPatientIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(…)
patients/
  PatientResponse.java                    + String adminNotes (después de emergencyContactPhoneE164)
  CreatePatientRequest.java               + @Size(max = 1000) String adminNotes (opcional)
  UpdatePatientRequest.java               adminNotes gana @Size(max = 1000)
  PatientService.java                     create guarda adminNotes; create y update: trim, y vacío → null
src/test/java/com/bellidental/patients/
  PatientRecordApiIT.java                 (nuevo)
docs/IMPLEMENTATION-PLAN.md               §3 (fila 006d), §4.1 y §4.3 (contrato nuevo)
```
No cambia ningún `Directory` ni la dependencia entre módulos: `appointments` ya guarda `patientId` como UUID.

## Criterios de aceptación
**G-4 — `GET /api/v1/appointments` (permiso `APPOINTMENT_READ`, sin cambios)**
1. **Sin `patientId`:** se comporta igual que hoy.
   - `from` y `to` siguen siendo obligatorios. Si falta alguno → 400 (`InvalidInputException`) con el mensaje
     "Indica 'from' y 'to'."
   - Se mantienen las validaciones actuales: `to` posterior a `from` y rango máximo de 31 días.
   - La agenda del frontend (`?from&to` y `?professionalId&from&to`) no cambia.
2. **Con `patientId` y sin rango:** devuelve todas las citas de ese paciente, de todos los estados (incluidas
   `CANCELLED`, `RESCHEDULED` y `NO_SHOW`), ordenadas por `startAt` ascendente.
   - Si el paciente no tiene citas o no existe → 200 con `data: []`, sin 404.
3. **Con `patientId`, `from` y `to`:** filtra por paciente y por rango, con las mismas validaciones del criterio 1.
   El límite de 31 días también se aplica.
4. **Con `patientId` y solo uno de `from`/`to`:** 400 con "Indica 'from' y 'to' juntos."
5. **`patientId` con `professionalId`:** 400 con "Filtra por paciente o por profesional, no por ambos." No se
   implementa la combinación porque ninguna pantalla la usa.
6. La respuesta es la misma `ApiResponse<List<AppointmentResponse>>` de hoy, sin campos nuevos.

**G-5 — `adminNotes` en pacientes**
7. `PatientResponse` incluye `adminNotes` (`string` o `null`) en `GET /patients/{id}`, `POST /patients` y
   `PUT /patients/{id}`.
   - `GET /patients?query=` sigue usando el mismo DTO que hoy: no se toca.
8. `POST /patients` acepta `adminNotes`, que es opcional y tiene como máximo 1000 caracteres. Si se pasa del
   límite → 400 por validación.
9. En `POST` y `PUT`, `adminNotes` se guarda recortada (trim).
   - Una cadena vacía o con solo espacios se guarda como `null`, así que en el `PUT` la observación se borra.
   - En el `PUT`, si `adminNotes` viene ausente o `null`, la observación no cambia, igual que el resto de los campos
     de la actualización.
10. Las observaciones son administrativas, no clínicas, así que no rompen la regla "`GET /patients/{id}` nunca
    devuelve datos clínicos" (§4.1). Se ven con `PATIENT_READ`, igual que el resto de la identificación.

**Plan**
11. En `docs/IMPLEMENTATION-PLAN.md`:
    - §3: fila `| 006d | Citas por paciente y adminNotes (G-4, G-5 del frontend) | — | 004, 006 | PatientRecordApiIT |`
      debajo de la 006c;
    - §4.3: el contrato de `GET /appointments` de los criterios 1 a 5;
    - §4.1: `adminNotes` en la respuesta y en el alta.

## Tests requeridos
- **Unit:** ninguno. No hay reglas puras nuevas en `domain/`, y las validaciones de parámetros se cubren en el IT.
- **Integration:** `PatientRecordApiIT`, con `TestcontainersConfiguration`, datos por API y semillas. 3 casos:
  1. `adminNotes_createReadClear_roundTrips`:
     - `POST /patients` con `adminNotes: "  Presupuesto ortodoncia $1.200  "` → 201 y
       `data.adminNotes == "Presupuesto ortodoncia $1.200"`;
     - `GET /patients/{id}` → el mismo valor;
     - `PUT /patients/{id}` con `adminNotes: ""` → `data.adminNotes == null`.
  2. `getAppointments_byPatientWithoutRange_returnsOnlyThatPatientAscending`:
     - crear un horario para un profesional de la semilla;
     - crear dos citas del paciente A en días distintos, la más lejana primero, y una del paciente B;
     - `GET /appointments?patientId=A` → 2 citas, ambas de A y ordenadas por `startAt` ascendente.
  3. `getAppointments_invalidFilters_returnBadRequest`: tres peticiones, cada una con su mensaje:
     - sin `patientId` y sin rango → 400 "Indica 'from' y 'to'.";
     - con `patientId` y solo `from` → 400 "Indica 'from' y 'to' juntos.";
     - con `patientId` y `professionalId` → 400 "Filtra por paciente o por profesional, no por ambos.".
- **Deben seguir pasando:** `AppointmentOverlapIT`, `AvailabilityIT`, `ScheduleApiIT` y `EncounterFlowIT`, que usan
  `GET /appointments` con rango o crean pacientes. También `PatientServiceTest`: si construye `CreatePatientRequest`
  por posición, se ajusta solo para agregar el argumento nuevo.

## Resultado
- **Archivos creados o modificados**:
  - `AppointmentRepository.java`: agregados métodos `findByPatientIdOrderByStartAtAsc` y `findByPatientIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc`.
  - `AppointmentController.java`: agregado parámetro `patientId` opcional en `GET /appointments`; `from` y `to` pasan a ser opcionales en el endpoint.
  - `AppointmentService.java`: validaciones y filtrado en `getAppointments`: sin `patientId` exige `from`/`to` obligatorios (máx 31 días); con `patientId` sin rango devuelve todas las citas del paciente ordenadas por `startAt` ascendente; con `patientId` y rango filtra por paciente y rango; validación de `from`/`to` juntos; rechazo de combinación simultánea de `patientId` y `professionalId` (400). Se eliminó la sobrecarga sin uso `getAppointments(professionalId, from, to)`. Se optimizó la resolución de profesionales inactivos con caché local `computeIfAbsent` sobre un mapa mutable inicializado con los profesionales activos.
  - `PatientResponse.java`: añadido campo `String adminNotes` después de `emergencyContactPhoneE164`, mapeado desde `p.getAdminNotes()`.
  - `CreatePatientRequest.java`: añadido campo `@Size(max = 1000) String adminNotes` (opcional). Se removió el constructor secundario de 11 argumentos.
  - `UpdatePatientRequest.java`: añadida validación `@Size(max = 1000)` sobre `adminNotes`.
  - `PatientService.java`: normalización de `adminNotes` con `trim` en `create` y `update`; cadenas vacías o con solo espacios se convierten a `null` (en `PUT` se borra la nota; si `adminNotes` es `null` o ausente se preserva el valor existente).
  - Tests actualizados para `CreatePatientRequest` con `null` en posición 12: `AppointmentOverlapIT`, `AvailabilityIT`, `PatientServiceTest`, `OdontogramFlowIT`, `EncounterFlowIT` y `PatientRecordApiIT.createPatient`.
  - `PatientRecordApiIT.java` (nuevo): 3 casos de integración (`adminNotes_createReadClear_roundTrips`, `getAppointments_byPatientWithoutRange_returnsOnlyThatPatientAscending`, `getAppointments_invalidFilters_returnBadRequest`).
  - `docs/IMPLEMENTATION-PLAN.md`: actualizada fila 006d en §3 y contratos en §4.1 y §4.3.
- **Resultado de los tests**:
  - Surefire: 83 tests ejecutados, 0 fallos, 0 errores, 0 omitidos.
  - Failsafe: 29 tests ejecutados, 0 fallos, 0 errores, 0 omitidos (incluyendo los 3 de `PatientRecordApiIT`).
  - Total `./mvnw -q clean verify`: **112 tests, 0 fallos, 0 errores, 0 omitidos**.
- **Problemas encontrados y supuestos tomados**:
  - Corrección aplicada: Se eliminó el constructor secundario de `CreatePatientRequest` y se ajustaron los 6 tests afectados pasando `null` como 12.° argumento.
  - Corrección aplicada: Se eliminó la sobrecarga no utilizada de `AppointmentService.getAppointments`.
  - Corrección aplicada (supuesto): Se utiliza un mapa local mutable `professionals` precargado con los profesionales activos, resolviendo con `computeIfAbsent` y `professionalDirectory.findById` aquellos inactivos una sola vez por profesional.

## Revisión
**Estado: CORRECCIONES** (revisión 1, Claude)

Verificado por Claude con `./mvnw -q clean verify`: surefire 83/83 y failsafe 29/29 (`PatientRecordApiIT` 3/3).
Los criterios 1 a 10 se cumplen en lo funcional. Claude completó el plan (criterio 11): en §4.3 detalló los filtros
de `GET /appointments` y corrigió las rutas de horarios de la 006c.

Corregir:
1. `CreatePatientRequest`: quitar el constructor secundario de 11 argumentos. Es código de producción que solo sirve
   a los tests. Como pedía la tarea, ajustar los tests que construyen el request por posición, añadiendo `null` como
   último argumento: `AppointmentOverlapIT`, `AvailabilityIT`, `PatientServiceTest`, `OdontogramFlowIT`,
   `EncounterFlowIT` y `PatientRecordApiIT.createPatient`.
2. `AppointmentService`: eliminar la sobrecarga `getAppointments(professionalId, from, to)`, que quedó sin uso.
3. `AppointmentService.getAppointments`: el respaldo `professionalDirectory.findById` para profesionales inactivos se
   acepta, porque el historial de un paciente puede incluir citas de profesionales ya inactivos. Pero hoy consulta la
   base de datos una vez por cada cita. Debe resolverse con `computeIfAbsent` sobre un mapa local, igual que
   `specialtyNames`, para hacer una consulta por profesional. Anotarlo en Resultado como supuesto.
4. Correr `./mvnw -q clean verify` y actualizar los totales en Resultado.


**Estado: APROBADO** (revisión 2, Claude)

Correcciones 1–4 aplicadas: se quitó el constructor secundario de `CreatePatientRequest` y los 6 tests pasan `null`
como último argumento; se eliminó la sobrecarga sin uso de `getAppointments`; los profesionales inactivos se buscan
una sola vez por profesional con `computeIfAbsent` sobre el mapa `professionals`.
`./mvnw -q clean verify` (Claude): surefire 83/83 y failsafe 29/29.

Commit: el código y los tests de la tarea, `docs/IMPLEMENTATION-PLAN.md` y este archivo. No incluir `proy-infor/`.
