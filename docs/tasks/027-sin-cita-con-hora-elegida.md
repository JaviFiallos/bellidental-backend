# 027 — Cita sin agenda (walk-in) con hora elegida en bloques de 15 min (G-15 del frontend)

## Objetivo
`POST /appointments/walk-in` siempre pone la cita en el primer hueco libre desde ahora con la duración por defecto de
la especialidad. Recepción necesita elegir la hora y la duración igual que al agendar (bloques de 15 min, turnos
mañana/tarde), a partir de la hora actual. El frontend ya pide los bloques con
`GET /availability?…&date=hoy&durationMinutes=15` (que no devuelve horas pasadas); falta que el walk-in acepte la hora.

## Archivos a tocar
```
src/main/java/com/bellidental/appointments/api/dto/WalkInRequest.java          (modif.) startAt?, endAt?
src/main/java/com/bellidental/appointments/application/AppointmentService.java (modif.) walkIn
src/test/java/com/bellidental/appointments/AppointmentOverlapIT.java           (modif.) casos nuevos (o un WalkInIT nuevo)
docs/IMPLEMENTATION-PLAN.md                                                    §4.3 walk-in, tabla §3 y D-29
```

## Criterios de aceptación
1. `WalkInRequest` gana `OffsetDateTime startAt` y `OffsetDateTime endAt`, ambos opcionales. Solo uno → 400
   "Indica la hora de inicio y de fin, o ninguna.".
2. Sin horas → comportamiento actual, sin cambios (primer hueco desde ahora, duración de la especialidad).
3. Con horas:
   - `startAt` es de **hoy** en la zona de la clínica; si no → 400 "La atención sin cita es solo para hoy.".
   - `startAt` ≥ inicio del bloque de 15 min actual (a las 10:07 se acepta 10:00); si no → 400 "La hora ya pasó.".
   - Duración: la misma validación de `POST /appointments` (006f: múltiplo de 15, 15–240 min) con su mismo mensaje.
   - Cruce con otra cita → 409 `APPOINTMENT_OVERLAP` (lo garantiza el EXCLUDE de la BD, como hoy).
   - Sigue exento del horario efectivo (como el walk-in actual) y queda `source = WALK_IN`, `checked_in_at = now`.
4. La respuesta es el mismo `AppointmentResponse`.

## Tests requeridos
**Unit:** ninguno.

**Integration** (+3): walk-in con `startAt`/`endAt` libres → 201 con esas horas, `source=WALK_IN` y `checkedInAt`;
mismas horas que una cita existente → 409; `startAt` de ayer o de hace 1 h → 400 con el mensaje.

## Resultado
- **Qué se hizo:**
  - Modificado `WalkInRequest.java` para añadir `OffsetDateTime startAt` y `OffsetDateTime endAt` opcionales, conservando constructor de 3 argumentos por compatibilidad.
  - Modificado `AppointmentService.java` en `walkIn`:
    - Validación: si solo viene uno de los dos campos de hora → 400 `"Indica la hora de inicio y de fin, o ninguna."`.
    - Si se omiten las horas, mantiene el cálculo automático del primer hueco libre del día con la duración por defecto de la especialidad.
    - Si se proporcionan horas: valida que `startAt` sea de hoy en la zona de la clínica (`"La atención sin cita es solo para hoy."`), que `startAt` sea mayor o igual al inicio del bloque de 15 min actual (`"La hora ya pasó."`), valida duración con `AppointmentDuration.validate` (15–240 min en múltiplos de 15), asigna `source = WALK_IN`, `checked_in_at = now` y mantiene exención de horario efectivo. Los solapamientos son detectados por la restricción `EXCLUDE` y traducidos a 409 `APPOINTMENT_OVERLAP`.
  - Añadidos 3 tests de integración a `AppointmentOverlapIT.java` cubriendo horas válidas, cruce con conflicto 409, y validaciones de hora pasada y día anterior (400).
  - Actualizado `docs/IMPLEMENTATION-PLAN.md` en §4.3.
- **Resultado de los tests:**
  - Surefire: `Tests run: 103, Failures: 0, Errors: 0, Skipped: 0`
  - Failsafe: `Tests run: 86, Failures: 0, Errors: 0, Skipped: 0`
- **Problemas y supuestos:**
  - Ninguno.


## Revisión
Estado: APROBADO

Cumple los 4 criterios; IMPLEMENTATION-PLAN actualizado (§3, §4.3, D-29). Correcciones menores hechas por Claude:
1. `walkIn` era muy largo: se separó en `chosenWalkInRange`, `firstFreeWalkInRange` y `currentBlockStart`
   (devuelven `TimeRange`); la validación "uno solo" quedó como `(startAt == null) != (endAt == null)`.
2. `WalkInRequest`: se quitaron los `@NotNull` del constructor de 3 argumentos (lo usa `AvailabilityIT`).

`./mvnw -q clean verify` (Claude): Surefire 103/0/0/0 · Failsafe 86/0/0/0.
