# 006b — Agenda: disponibilidad, walk-in y AppointmentScheduler

> Requiere la 006a aprobada y en commit.

## Objetivo
Segunda mitad del módulo `com.bellidental.appointments` (RF07, O4; base de RF12/RF15; plan §4.3): huecos libres por
profesional y día, atención sin cita (walk-in) y la interfaz pública `AppointmentScheduler` que usarán la atención
clínica (008) y el bot (015–017). Sin migraciones.

## Archivos a tocar
```
appointments/
  AppointmentScheduler.java        (nuevo) interfaz pública
  AppointmentSummary.java          (nuevo) record público: id, patientId, professionalId, specialtyId, startAt, endAt,
                                   status, source, checkedInAt, rescheduledFromId
  AvailableSlot.java               (nuevo) record público: startAt, endAt (OffsetDateTime)
  BookAppointmentCommand.java      (nuevo) record público: patientId, professionalId, specialtyId, startAt,
                                   source, createdBy, reason
  api/AvailabilityController.java  (nuevo)
  api/AppointmentController.java   (añadir walk-in)
  api/dto/WalkInRequest.java       (nuevo)
  application/AvailabilityService.java        (nuevo)
  application/AppointmentSchedulerImpl.java   (nuevo)
  application/AppointmentService.java         (añadir walkIn, book con source explícito, markAttended)
  domain/SlotCalculator.java       (nuevo, Java puro)
  domain/WalkInSlotFinder.java     (nuevo, Java puro)
  domain/TimeRange.java            (nuevo) record (startAt, endAt) para citas ocupadas
persistence/repository/AppointmentRepository.java  (añadir consulta de citas activas por profesional y rango)
src/test/java/com/bellidental/appointments/
  domain/SlotCalculatorTest, domain/WalkInSlotFinderTest, AvailabilityIT
```
Dependencias: las mismas de la 006a. `AppointmentScheduler` y sus records son lo único que otros módulos importarán.

## Criterios de aceptación
**Citas activas** (para huecos y walk-in)
1. `AppointmentRepository.findActiveOverlapping(professionalId, from, to)`: citas con `status IN (PENDING, CONFIRMED)`
   o `ATTENDED`, es decir, `status NOT IN (CANCELLED, NO_SHOW, RESCHEDULED)` (igual que el `EXCLUDE`), que se
   cruzan con `[from, to)`, ordenadas por `startAt`.

**Disponibilidad**
2. `SlotCalculator.calculate(LocalDate day, List<Shift> shifts, int durationMinutes, List<TimeRange> busy,
   OffsetDateTime now, ZoneId zone)` → `List<AvailableSlot>`:
   - por cada turno, candidatos desde `startTime` en pasos de `durationMinutes`;
   - un candidato vale si termina ≤ `endTime` del turno, no se cruza con ningún `busy` (extremos que se tocan no cruzan)
     y empieza después de `now`;
   - resultado en orden cronológico, con `OffsetDateTime` en la zona de la clínica.
3. `GET /api/v1/availability?professionalId=&specialtyId=&date=` (`APPOINTMENT_READ`):
   - especialidad inexistente → 404; profesional sin esa especialidad activa → 409;
   - turnos con el `EffectiveScheduleResolver` de la 006a, duración de `findSpecialty`, ocupadas con el punto 1;
   - `date` anterior a hoy → lista vacía. Responde `List<AvailableSlot>`.

**Walk-in** (paciente sin cita, O4)
4. `WalkInSlotFinder.findFirst(OffsetDateTime now, int durationMinutes, List<TimeRange> busy, OffsetDateTime endOfDay)`
   → `Optional<OffsetDateTime>`: candidato = `now` truncado al minuto; mientras se cruce con una cita ocupada, el
   candidato pasa al `endAt` de esa cita; vale si `candidato + duración ≤ endOfDay`. No usa el horario del profesional.
5. `POST /api/v1/appointments/walk-in` (`APPOINTMENT_CREATE`, 201). `WalkInRequest`: `patientId`, `professionalId`,
   `specialtyId`. Mismas validaciones de paciente y especialidad de la 006a; `endOfDay` = 23:59 del día local;
   sin hueco → 409 "sin hueco hoy para este profesional". Crea cita `source=WALK_IN`, `status=PENDING`,
   `checkedInAt=now`, `createdBy` = usuario actual. La traducción del cruce de la 006a sigue aplicando (carrera) → 409.
   Publica `AppointmentEvent(CREATED)`.

**AppointmentScheduler** (implementado delegando en `AppointmentService` y `AvailabilityService`; sin lógica propia)
6. Métodos:
   - `Optional<AppointmentSummary> findById(UUID appointmentId)`;
   - `List<AvailableSlot> findAvailableSlots(UUID professionalId, Long specialtyId, LocalDate date)`;
   - `AppointmentSummary book(BookAppointmentCommand command)`: mismas reglas que el POST de la 006a pero con
     `source` y `createdBy` del comando (el bot pasará `BOT` y el usuario `bot_whatsapp`); `BOT` debe caer en horario;
   - `AppointmentSummary reschedule(UUID appointmentId, OffsetDateTime newStartAt, UUID requestedBy)`: la operación de
     la 006a, con `createdBy = requestedBy` y la `source` de la original;
   - `void confirm(UUID appointmentId)`, `void cancel(UUID appointmentId)`;
   - `void markAttended(UUID appointmentId)`: transición `attend` de la 006a (con check-in → ATTENDED; si no → 409);
     publica `AppointmentEvent(STATUS_CHANGED)`.
7. El POST y el reschedule HTTP de la 006a pasan a usar internamente el mismo método que `book`/`reschedule`
   (sin duplicar reglas): el controlador arma el comando con la `source` del usuario actual.

**General**
8. Errores como en la 006a. No se tocan migraciones ni otros módulos.

## Tests requeridos
- Unit `domain/SlotCalculatorTest` (2 casos):
  1. turno 08:00–10:00, duración 30, cita 08:30–09:00 ocupada, `now` 07:00 → 08:00, 09:00, 09:30;
  2. turno 08:00–09:45 → el hueco 09:30–10:00 se descarta.
- Unit `domain/WalkInSlotFinderTest` (2 casos): dos citas seguidas desde `now` → fin de la segunda;
  no cabe antes de `endOfDay` → vacío.
- Integration `AvailabilityIT` (secretaria de la semilla; paciente creado vía API; horario por fecha 08:00–20:00 de un
  día futuro):
  1. crear una cita a las 10:00 → `GET /availability` de ese día no incluye 10:00 e incluye el hueco siguiente;
  2. `AppointmentScheduler.book` con `source=BOT` a las 21:00 → `BusinessException`; a las 11:00 → `source=BOT`;
  3. `POST /appointments/walk-in` hoy → 201 con `checkedInAt` y `source=WALK_IN` (profesional sin horario hoy).
- `AppointmentOverlapIT` de la 006a debe seguir pasando.

## Resultado
Ejecutado por Haiku 5.5 (obrero) en lugar de Gemini, a petición del usuario.

**Archivos creados:**
- `appointments/AppointmentScheduler.java`, `AppointmentSummary.java`, `AvailableSlot.java`, `BookAppointmentCommand.java` (interfaz y records públicos).
- `appointments/domain/TimeRange.java`, `SlotCalculator.java`, `WalkInSlotFinder.java` (Java puro).
- `appointments/application/AvailabilityService.java`, `AppointmentSchedulerImpl.java`.
- `appointments/api/AvailabilityController.java`, `appointments/api/dto/WalkInRequest.java`.
- Tests: `domain/SlotCalculatorTest.java`, `domain/WalkInSlotFinderTest.java`, `AvailabilityIT.java`.

**Archivos modificados:**
- `persistence/repository/AppointmentRepository.java`: `findActiveOverlapping` (default method sobre `findOverlappingWithStatuses`, con estados PENDING, CONFIRMED y ATTENDED).
- `appointments/application/AppointmentService.java`: `book(request, source, createdBy)` público (reglas comunes), `walkIn`, `markAttended`, `reschedule(id, req, requestedBy)`. Helpers `loadActivePatient`, `loadBookableSpecialty`, `persistAndRespond`, `saveOrTranslateOverlap`.
- `appointments/api/AppointmentController.java`: `POST /api/v1/appointments/walk-in` (201, `APPOINTMENT_CREATE`).

**Resultado de los tests (`./mvnw clean verify`, EXIT 0):**
- Surefire: 72 tests, 0 fallos, 0 errores (incluye `SlotCalculatorTest` 2, `WalkInSlotFinderTest` 2).
- Failsafe: 16 tests, 0 fallos, 0 errores (incluye `AvailabilityIT` 3, `AppointmentOverlapIT` 2).

**Problemas y supuestos:**
1. Un test de 006a fallaba: `AppointmentServiceTest.reschedule_updatesOriginalAndCreatesNew` tenía un stub de `currentUser.professionalId()` que ya no se usa (`UnnecessaryStubbingException`). Causa: la reprogramación toma ahora la `source` de la cita original. Quité solo ese stub; el test no cambió su aserción.
2. La reprogramación HTTP usa la `source` de la original (antes usaba la del usuario actual). Así se pide en la 006b para el `AppointmentScheduler`, y el HTTP comparte el método.
3. La `source` del POST HTTP la decide `AppointmentService.create` (PROFESSIONAL/RECEPTION), no el controlador. Lo dejé en el servicio para no duplicar la regla.
4. `AppointmentSchedulerImpl.findById` lee `AppointmentRepository` directamente (mapeo a `AppointmentSummary`); el resto delega en los servicios.
5. `AvailabilityIT` depende de la hora: el caso de walk-in requiere que quede hueco hoy hasta 23:59 (con 30 min); después de ~23:29 devolvería 409 "Sin hueco hoy".
6. `AvailabilityIT` supone que la duración de la especialidad (30 o 60 min en la semilla) divide 120; así el hueco que empieza al terminar la cita cae en la cuadrícula de 08:00.
7. Cada caso de `AvailabilityIT` usa un día distinto (+14, +15 y hoy) para no chocar con los horarios de otros tests.
8. Los 4 unit tests de dominio y el `AvailabilityIT` siguen el mínimo de `testing/SKILL.md`; no se añadió `AppointmentSchedulerImplTest`.

## Revisión
**Estado: APROBADO** (revisión 1, Claude)

Verificado con `./mvnw clean verify`: surefire 72/72, failsafe 16/16 (`AvailabilityIT` 3, `AppointmentOverlapIT` 2).
Cumplidos:
- `findActiveOverlapping` con los mismos estados que el `EXCLUDE`.
- `SlotCalculator`, `WalkInSlotFinder` y `TimeRange` en `domain/` sin Spring ni entidades.
- `/availability` con 404/409 y fecha pasada → lista vacía.
- Walk-in con `checkedInAt`, `source=WALK_IN` y la traducción del cruce compartida (`saveOrTranslateOverlap`).
- `AppointmentScheduler` delega en los servicios. El POST, el reschedule HTTP y el bot comparten `book` sin duplicar reglas.
- Tests dentro del mínimo de `testing/SKILL.md`.

Supuestos aceptados:
- La `source` del POST se decide en `create` y no en el controlador.
- Reprogramar conserva la `source` de la original.
- El walk-in del IT depende de la hora (antes de ~23:29).

Corrección mínima de Claude: `java.util.UUID.randomUUID()` → `UUID.randomUUID()` en `AppointmentOverlapIT` (de la 006a).
