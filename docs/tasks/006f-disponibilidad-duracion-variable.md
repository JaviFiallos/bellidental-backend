# 006f — Disponibilidad con duración elegida (bloques de 15 min)

> Corrección pequeña sobre la 006b (disponibilidad). Bloquea la tarea 004c del frontend (duración variable de la
> cita en el slot-picker).

## Objetivo
Recepción necesita agendar citas de 15, 30, 45 o 60 minutos, o más largas, sin depender de la duración por defecto
de la especialidad. El frontend mostrará bloques libres de 15 min y la secretaria elegirá la hora de inicio y la
duración, en múltiplos de 15.

Estado actual:
- `GET /availability` corta los huecos con `specialty.defaultDurationMinutes()`, y el paso entre un hueco y el
  siguiente es esa misma duración (`SlotCalculator`). Con una especialidad de 30 min nunca aparecen las 09:15 ni
  las 09:45, y no se pueden pedir bloques de 15.
- `POST /appointments` y `POST /appointments/{id}/reschedule` **ya aceptan** un `endAt` opcional y validan el
  horario efectivo y el cruce. No hace falta cambiarles el contrato, solo acotar la duración que aceptan.
- Al reprogramar sin `endAt`, la cita nueva toma la duración por defecto de la especialidad y pierde la duración
  original. No se cambia aquí, porque el frontend enviará siempre `endAt`.

Sin migraciones y sin cambios en `SlotCalculator`: si el paso es igual a la duración, pedir `durationMinutes=15` ya
devuelve los bloques de 15.

## Archivos a tocar
```
appointments/
  api/AvailabilityController.java          + @RequestParam(required = false) Integer durationMinutes
  application/AvailabilityService.java     findAvailableSlots(professionalId, specialtyId, date, durationMinutes):
                                             usa durationMinutes si llega; si no, defaultDurationMinutes
  application/AppointmentService.java      book(): si llega endAt, valida la duración (ver criterio 3)
src/test/java/com/bellidental/appointments/
  AvailabilityIT.java                      + 1 caso
docs/IMPLEMENTATION-PLAN.md                §4.x de disponibilidad: parámetro nuevo y límites de duración
```
No cambian los DTO, `AvailableSlot`, `SlotCalculator`, el walk-in ni ningún `Directory`. El walk-in sigue usando la
duración por defecto de la especialidad.

## Criterios de aceptación
1. `GET /api/v1/availability?professionalId&specialtyId&date&durationMinutes=15` devuelve bloques de 15 min
   (`endAt − startAt = 15 min`), cada uno empezando donde terminó el anterior a partir del inicio del turno. Omite
   los bloques que se cruzan con citas activas y los que empiezan antes de `now`.
2. Sin `durationMinutes`, el comportamiento es el de hoy: los tests existentes siguen pasando sin cambios.
3. `durationMinutes` que no sea múltiplo de 15 o que esté fuera de 15–240 → 400 con el mensaje
   `"La duración debe ser múltiplo de 15 minutos, entre 15 y 240."`. Usar `InvalidInputException` y una constante
   con nombre para los límites; nada de números sueltos.
4. `book()` (crear y reprogramar): si llega `endAt`, `endAt − startAt` debe cumplir la misma regla. Si no la cumple
   → 400 con el mismo mensaje. Si `endAt` no llega, el comportamiento es el de hoy. Las dos validaciones comparten
   un único método o constante.
5. Una cita de 60 min creada con `endAt` respeta el cruce: los bloques de 15 que caen dentro de esa hora no
   aparecen en `/availability?durationMinutes=15`.

## Tests requeridos
**Integration** (`AvailabilityIT`, 1 caso nuevo):
- `getAvailability_withDuration15_returnsQuarterBlocksAndSkipsLongAppointment`: crear una cita 10:00–11:00 con
  `endAt` → `GET /availability?...&durationMinutes=15` → no contiene 10:00, 10:15, 10:30 ni 10:45; contiene 09:45
  y 11:00, y todos los bloques duran 15 min. En el mismo test, `durationMinutes=20` → 400.

**Unit:** ninguno (`SlotCalculator` no cambia).

## Resultado
- **Archivos creados o modificados:**
  - `src/main/java/com/bellidental/appointments/domain/AppointmentDuration.java`: nueva clase final de dominio con constructor privado que encapsula las constantes `MIN_MINUTES = 15`, `MAX_MINUTES = 240`, `STEP_MINUTES = 15`, el mensaje de error y los métodos estáticos `validate(int minutes)` y `validate(OffsetDateTime startAt, OffsetDateTime endAt)`.
  - `src/main/java/com/bellidental/appointments/api/AvailabilityController.java`: parámetro opcional `@RequestParam(required = false) Integer durationMinutes` en `GET /api/v1/availability`.
  - `src/main/java/com/bellidental/appointments/application/AvailabilityService.java`: se restauró `@RequiredArgsConstructor` (eliminando el constructor explícito y métodos estáticos de validación). Se sobrecargó `findAvailableSlots` para recibir `durationMinutes`, validándolo con `AppointmentDuration.validate(durationMinutes)` si llega.
  - `src/main/java/com/bellidental/appointments/application/AppointmentService.java`: en `book()`, si `endAt != null`, se valida con `AppointmentDuration.validate(request.startAt(), endAt)`. Si no llega, se usa la duración por defecto de la especialidad.
  - `src/test/java/com/bellidental/appointments/AvailabilityIT.java`: caso de integración `getAvailability_withDuration15_returnsQuarterBlocksAndSkipsLongAppointment`.
  - `docs/IMPLEMENTATION-PLAN.md`: documentado en §4.3 el parámetro `durationMinutes` y límites de duración en agendamiento y disponibilidad.

- **Resultado de los tests:**
  - Surefire: run=83, fail=0, err=0, skip=0
  - Failsafe: run=36, fail=0, err=0, skip=0
  - Total: 119 tests ejecutados y pasando exitosamente.

- **Problemas encontrados y supuestos tomados:**
  - Se aplicaron las 3 correcciones de revisión: restauración de `@RequiredArgsConstructor`, traslado de las reglas a `AppointmentDuration` y simplificación de `validate(startAt, endAt)`.
  - Verificación limpia `./mvnw -q clean verify` exitosa con 119 tests pasando.

## Revisión
Estado: APROBADO

Correcciones 1–3 aplicadas: `@RequiredArgsConstructor` restaurado, regla en `appointments/domain/AppointmentDuration`,
validación simplificada. 119 tests sin fallos (83 + 36).
