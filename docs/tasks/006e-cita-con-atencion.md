# 006e — La cita trae su atención (G-1 del frontend)

> Corrección pequeña sobre la 006 (agenda). Bloquea la tarea 007 del frontend ("Mis citas de hoy" del odontólogo).

## Objetivo
La pantalla del odontólogo necesita saber, por cada cita, si ya tiene una atención y en qué estado está, para:
- mostrar "En atención" mientras la atención está abierta;
- ofrecer "Continuar", que abre la atención existente, o "Ver", para una atención ya completada;
- no ofrecer "Continuar" sobre una atención anulada, porque cualquier cambio daría 409.

Hoy `AppointmentResponse` no dice nada de la atención. El frontend tendría que hacer una petición por cita, y no
existe ningún endpoint para buscar la atención a partir de la cita.

Sin migraciones: `encounters.appointment_id` ya es `UNIQUE`, así que hay como máximo una atención por cita.

**Decisión de diseño (D-15):** `clinical` ya depende de `appointments` (por `AppointmentScheduler`), así que
`appointments` no puede llamar a `ClinicalDirectory` sin crear un ciclo. `AppointmentService` lee el vínculo
directamente con `EncounterRepository`, que está en `persistence/repository` y lo comparten todos los módulos. Solo lo
lee y nunca escribe en `encounters`. Las reglas de la atención siguen siendo del módulo `clinical`.

## Archivos a tocar
```
persistence/repository/
  EncounterRepository.java                + List<Encounter> findByAppointmentIdIn(Collection<UUID> appointmentIds)
appointments/
  api/dto/AppointmentResponse.java        + UUID encounterId, EncounterStatus encounterStatus, boolean encounterVoided
                                            (al final, después de rescheduledFromId)
  application/AppointmentService.java     toResponse recibe la atención (o null); la lista la resuelve en lote
  application/AppointmentSchedulerImpl.java   solo si construye AppointmentResponse: ajustar al constructor nuevo
src/test/java/com/bellidental/appointments/
  AppointmentEncounterLinkIT.java         (nuevo)
docs/IMPLEMENTATION-PLAN.md               §3 (fila 006e), §4.3 (campos nuevos) y §6 (D-15)
```
No cambian `AppointmentSummary`, `AppointmentScheduler` ni ningún `Directory`. `appointments` no importa nada del
paquete `com.bellidental.clinical`, porque `EncounterStatus` ya está en `shared/enums`.

## Criterios de aceptación
1. Toda respuesta con `AppointmentResponse` incluye tres campos nuevos:
   - `encounterId` (`uuid` o `null`);
   - `encounterStatus` (`"OPEN"`, `"COMPLETED"` o `null`);
   - `encounterVoided` (`boolean`).

   Esto vale para todos los endpoints que devuelven `AppointmentResponse`: `GET /appointments` (todas las variantes),
   `GET /appointments/{id}`, `POST /appointments`, `POST /appointments/walk-in` y
   `POST /appointments/{id}/reschedule`. (`confirm`, `cancel`, `check-in` y `no-show` responden `ApiResponse<Void>`
   y no cambian.)
2. **Sin atención:** `encounterId = null`, `encounterStatus = null` y `encounterVoided = false`. Es el caso de toda
   cita recién creada o reprogramada: la cita nueva de un reprogramar no hereda la atención de la original.
3. **Con atención:** `encounterId` y `encounterStatus` toman el `id` y el `status` de la atención.
   `encounterVoided = (voided_at != null)`. Una atención anulada se sigue informando, con su id y su estado, porque
   la cita sigue ligada a ella (`UNIQUE`). El frontend usa `encounterVoided` para no mostrar "En atención" ni
   "Continuar".
4. **Sin N+1:** `GET /appointments` hace una sola consulta `findByAppointmentIdIn(ids)` para toda la lista y arma un
   `Map<UUID, Encounter>` por `appointmentId`. Si la lista está vacía, no se consulta. Los endpoints que devuelven
   una sola cita usan `findByAppointmentId`, que ya existe. Las citas recién creadas pueden pasar `null` sin
   consultar.
5. Lo demás no cambia: los permisos (`APPOINTMENT_READ` / `APPOINTMENT_MANAGE`), las validaciones de 006d y el
   orden de los campos existentes.
6. **Plan** (`docs/IMPLEMENTATION-PLAN.md`):
   - §3: fila `| 006e | La cita trae su atención (G-1 del frontend) | — | 006, 008 | AppointmentEncounterLinkIT |`
     debajo de la 006d;
   - §4.3: después de los filtros de la 006d, una línea con los tres campos y las reglas 2 y 3;
   - §6 "Tomadas": `| D-15 | appointments lee el vínculo cita→atención con EncounterRepository (solo lectura) para
     evitar el ciclo con clinical | §4.3 |`.

## Tests requeridos
- **Unit:** ninguno. No hay reglas puras nuevas.
- **Integration:** `AppointmentEncounterLinkIT`, con `TestcontainersConfiguration`, datos por API y semillas.
  Para crear el horario, las citas, el check-in y la apertura de la atención, reutiliza la preparación de
  `EncounterFlowIT`, copiando los helpers que hagan falta. 2 casos:
  1. `getAppointments_withAndWithoutEncounter_reportsLink`:
     - un profesional de la semilla con dos citas el mismo día;
     - check-in de la primera y apertura de la atención como ese odontólogo
       (`POST /appointments/{id}/encounter`);
     - `GET /appointments?professionalId&from&to` debe dar:
       - en la primera: `encounterId` igual al id devuelto al abrir, `encounterStatus == "OPEN"` y
         `encounterVoided == false`;
       - en la segunda: `encounterId == null`, `encounterStatus == null` y `encounterVoided == false`.
  2. `getAppointment_afterVoidingEncounter_reportsVoided`:
     - abrir una atención y anularla (`POST /encounters/{id}/void` con motivo);
     - `GET /appointments/{id}` debe dar el mismo `encounterId` y `encounterVoided == true`.
- **Deben seguir pasando:**
  - `AppointmentOverlapIT`, `AvailabilityIT`, `ScheduleApiIT`, `PatientRecordApiIT`, `EncounterFlowIT` y
    `OdontogramFlowIT`;
  - los unit tests que construyan `AppointmentResponse` por posición: se ajustan solo para agregar los tres
    argumentos nuevos.

## Resultado
- **Qué se hizo:**
  - `persistence/repository/EncounterRepository.java`: se agregó la consulta por lotes `List<Encounter> findByAppointmentIdIn(Collection<UUID> appointmentIds)`.
  - `appointments/api/dto/AppointmentResponse.java`: se añadieron al final los campos `UUID encounterId`, `EncounterStatus encounterStatus` y `boolean encounterVoided`.
  - `appointments/application/AppointmentService.java`: se inyectó `EncounterRepository` (cumpliendo D-15 sin ciclos hacia `clinical`), se adaptó `toResponse` para recibir la atención (o `null`), se cargaron las atenciones en lote con `findByAppointmentIdIn` en `getAppointments` para evitar N+1, y en `getById` mediante `findByAppointmentId`.
  - `src/test/java/com/bellidental/appointments/application/AppointmentServiceTest.java`: se agregó el mock de `EncounterRepository` para los unit tests existentes.
  - `src/test/java/com/bellidental/appointments/AppointmentEncounterLinkIT.java`: se creó el test de integración con los 2 casos requeridos (`getAppointments_withAndWithoutEncounter_reportsLink` y `getAppointment_afterVoidingEncounter_reportsVoided`).
  - `docs/IMPLEMENTATION-PLAN.md`: se documentó la tarea 006e en §3, los campos vinculados en §4.3 y la decisión D-15 en §6.
- **Resultado de los tests:**
  - Surefire: Tests run: 83, Failures: 0, Errors: 0, Skipped: 0.
  - Failsafe: Tests run: 31, Failures: 0, Errors: 0, Skipped: 0.
  - Verificación completa con `./mvnw -q clean verify` exitosa (114 tests en total pasaron).
- **Problemas encontrados y supuestos tomados:**
  - No se presentaron problemas. Se mantuvo `AppointmentSchedulerImpl` intacto ya que accede a los accessors del record y no invoca el constructor posicional.

## Revisión
**Estado: APROBADO** (revisión 1, Claude)

Verificado por Claude con `./mvnw -q clean verify`: surefire 83/83 y failsafe 31/31 (`AppointmentEncounterLinkIT` 2/2).
- Criterios 1 a 6 cumplidos.
- La lista resuelve las atenciones en una sola consulta (`findByAppointmentIdIn`) y no consulta si está vacía.
- `getById` usa `findByAppointmentId`.
- `create`, `walk-in` y `reschedule` pasan `null`.
- `appointments` no importa nada de `com.bellidental.clinical`, como pide D-15.
- El plan quedó actualizado en §3, §4.3 y §6.

Ajustes de Claude:
- Corrigió el criterio 1: `confirm`, `cancel`, `check-in` y `no-show` devuelven `Void`, así que no llevan
  `AppointmentResponse`. Gemini hizo bien en no tocarlos.
- Quitó la línea en blanco sobrante al final de `AppointmentResponse` y de `EncounterRepository`.

Commit: el código y los tests de la tarea, `docs/IMPLEMENTATION-PLAN.md` y este archivo. No incluir `proy-infor/`.

