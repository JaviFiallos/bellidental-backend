# 033 — Bot: horario semanal de los profesionales

## Objetivo
El bot solo conoce el horario general de la clínica (`application.clinic.hours`, texto fijo). El paciente quiere saber
cuándo atiende su doctor o si la clínica atiende un día concreto ("¿atienden el sábado?", "¿el 24/12?").
Nuevo endpoint de **solo lectura** con el horario efectivo (§4.3, D-1) de los profesionales activos, que ya incluye
las excepciones por fecha y los días con `available=false` (feriados registrados por la secretaria).

Solo expone nombre del profesional y horas de turno, en una ventana de 14 días (la misma que `/availability/days`):
nada de citas, ocupación ni motivos de excepción. Sin migraciones. El horario es por profesional, no por
especialidad: un profesional con varias especialidades aparece con el mismo horario en cada una.

## Archivos a tocar
```
src/main/java/com/bellidental/appointments/
  AppointmentScheduler.java        + findEffectiveSchedule(UUID professionalId, LocalDate from, LocalDate to)
  EffectiveDay.java                record público (date, List<Shift>) con Shift(LocalTime startTime, LocalTime endTime)
  application/AppointmentSchedulerImpl.java   delega en ScheduleService.getEffectiveSchedule
src/main/java/com/bellidental/bot/
  api/BotAgendaController.java     GET /schedule
  api/dto/BotProfessionalScheduleResponse.java
  application/BotAvailabilityService.java     getSchedule(from, to, specialtyId, professionalId)
src/test/java/com/bellidental/bot/
  BotAvailabilityIT.java           +2 casos
docs/IMPLEMENTATION-PLAN.md       fila 033 y fila en la tabla de §4.11
```

## Criterios de aceptación
1. `GET /api/v1/bot/schedule?from=&to=&specialtyId=&professionalId=` (los dos últimos opcionales), con `X-Bot-Key`.
   `data`: `[{professionalId, professionalName, days: [{date, shifts: [{startTime, endTime}]}]}]`.
   - `professionalName` con el mismo formato que `GET /bot/professionals` (`displayName`).
   - `days` trae **todas** las fechas de `from` a `to`, en orden; día sin atención → `shifts: []`.
   - `startTime`/`endTime` como `"HH:mm"`.
2. Profesionales: con `professionalId` → solo ese si está activo (si no, `[]`); con `specialtyId` → los activos de
   esa especialidad (`findActiveBySpecialty`); sin filtros → `findActive()`. Ordenados por nombre.
3. Rango: `from >= hoy` y `to <= hoy + 13` (zona `America/Guayaquil`) y `from <= to`; si no → 400 con
   `ApiResponse` ("Rango de fechas no permitido.").
4. El bot obtiene el horario por `AppointmentScheduler.findEffectiveSchedule` (interfaz pública del módulo); no
   importa `appointments.application` ni `appointments.domain`. Misma lógica que `GET
   /professionals/{id}/schedules/effective`, sin duplicarla.
5. Plan: fila `| 033 | Bot: horario semanal de profesionales (/bot/schedule) | — | 016a | BotAvailabilityIT (casos
   nuevos) |` y en §4.11 `| GET /bot/schedule?from&to&specialtyId?&professionalId? | horario efectivo por día de
   los profesionales activos, máx. 14 días | 033 |`.
6. `./mvnw -q clean verify` pasa.

## Tests requeridos
- **Unit:** ninguno.
- **Integration:** `BotAvailabilityIT`:
  1. «`/bot/schedule` de hoy a hoy+6 → el profesional de la semilla trae 7 días y los turnos de su horario
     semanal».
  2. «`to` más allá de hoy+13 → 400».

## Resultado

**Archivos creados**
- `src/main/java/com/bellidental/appointments/EffectiveDay.java` (record público `EffectiveDay(date, List<Shift>)` con `Shift(startTime, endTime)` anidado)
- `src/main/java/com/bellidental/bot/api/dto/BotProfessionalScheduleResponse.java` (con `ScheduleDay` y `ScheduleShift` anidados, horas en "HH:mm")

**Archivos modificados**
- `appointments/AppointmentScheduler.java`: + `findEffectiveSchedule(professionalId, from, to)`
- `appointments/application/AppointmentSchedulerImpl.java`: delega en `ScheduleService.getEffectiveSchedule` y mapea a `EffectiveDay`
- `bot/api/BotAgendaController.java`: `GET /api/v1/bot/schedule`
- `bot/application/BotAvailabilityService.java`: `getSchedule(from, to, specialtyId, professionalId)`, validación de rango (400 `Rango de fechas no permitido.` vía `InvalidInputException`), filtros y orden por nombre
- `src/test/java/com/bellidental/bot/BotAvailabilityIT.java`: +2 casos
- `docs/IMPLEMENTATION-PLAN.md`: fila 033 (tabla de tareas, tras 032) y fila `GET /bot/schedule` en §4.11. La línea D-33 sin commitear no se tocó.

**Tests**
- `BotAvailabilityIT` aislado: Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 (failsafe).
- `./mvnw -q clean verify` (una vez): exit 0. Surefire: 103 tests, 0 fallos, 0 errores, 0 omitidos. Failsafe: 101 tests, 0 fallos, 0 errores, 0 omitidos. Los totales son la suma de los reports por clase (`target/surefire-reports`, `target/failsafe-reports`), no la línea de resumen de Maven, que con `-q` no aparece.

**Supuestos**
- `Shift` de `appointments/domain` no puede cruzar al bot, así que `EffectiveDay` define su propio `Shift` anidado y el impl hace el mapeo. La tarea no preveía un archivo extra para `Shift`.
- Con `specialtyId` y `professionalId` a la vez se aplica la intersección: lista por especialidad filtrada por id. Sin `specialtyId`, la base es `findActive()`.
- El caso 1 crea un horario semanal (`weekday`) para el día de la semana de hoy+3 (nunca hoy, para no alterar `urgentSlots` que espera "sin huecos hoy"). Las semillas no traen horario semanal del profesional; los `specificDate` de `setUp` no cubren ese día. Riesgo: el horario semanal queda en la BD compartida del contexto de tests.
- `from`/`to` son parámetros requeridos, como en el resto de endpoints del bot. Un parámetro ausente cae en `handleGeneric` (500); no se cambió el manejador global.

## Revisión
Estado: APROBADO

- Contrato, rango de 14 días, filtros y formato del nombre igual que `/bot/professionals`. El bot usa solo la
  interfaz pública `AppointmentScheduler`. Totales verdes.
- Observación (no bloquea): el caso 1 deja un horario semanal en la BD compartida de los IT. Hoy no afecta a los
  demás casos; si algún día falla un IT de urgencias o de disponibilidad, revisar esto primero.
- Commit: la línea D-33 del plan no es de esta tarea y queda fuera del commit, en el árbol de trabajo.
