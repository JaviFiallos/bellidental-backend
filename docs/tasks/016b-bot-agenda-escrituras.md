# 016b — Bot (Java): agendar, reprogramar, cancelar y confirmar

## Objetivo
Escrituras de la agenda desde el bot (RF12, RF15) con sus reglas:
- límites de citas activas (D-9);
- cancelar o reprogramar solo hasta 2 h antes (D-10);
- pertenencia de la cita al teléfono.

Plan §4.11. El agente solo llama a estos endpoints después de que el paciente pulse *Confirmar*. Además, las
respuestas 409 llevan un `code` estable para que el agente sepa qué pasó sin leer textos.

**Estructura**: amplía `bot`. Añade `bot/domain/` para la regla pura de límites. Usa `AppointmentScheduler`,
`PatientDirectory` y `BotIdentityService` (015a).

## Archivos a tocar
```
src/main/resources/application.yml            + application.bot.limits.max-active-per-phone: 3
                                               + application.bot.limits.change-min-hours: 2
config/BotProperties.java                      + limits (record anidado: maxActivePerPhone, changeMinHours)
shared/
  response/ApiResponse.java                    + code (String, opcional; NON_NULL ya oculta el null)
                                               + static error(String message, String code)
  exception/BusinessException.java             + constructor (message, code) y getCode()
  exception/GlobalExceptionHandler.java        BusinessException → ApiResponse.error(message, code)
appointments/domain/AppointmentOverlapException.java   pasa code "APPOINTMENT_OVERLAP"
bot/
  api/BotAgendaController.java                 + POST appointments, reschedule, cancel, confirm
  api/dto/                                     + BotBookRequest, BotRescheduleRequest, BotPhoneRequest
  application/BotBookingService.java           (nuevo)
  domain/BotBookingLimits.java                 (nuevo) regla pura D-9
src/test/java/com/bellidental/bot/
  BotAppointmentIT.java                        (nuevo)
  domain/BotBookingLimitsTest.java             (nuevo)
```

## Criterios de aceptación
1. **Códigos de error**:
   - `ApiResponse` admite `code`; las respuestas actuales no cambian, porque `code` es null y no se serializa.
   - 409 con `code`:
     - `APPOINTMENT_OVERLAP`: el hueco ya se ocupó;
     - `BOT_LIMIT_REACHED`: se superó un límite de D-9;
     - `BOT_TOO_LATE`: faltan menos de 2 h.
   - Otros 409 de la agenda (estado inválido) siguen sin `code`.
2. **Pertenencia**:
   - Cada operación sobre una cita existente valida que el paciente de la cita tenga ese `phone` (normalizado);
     si no → `404 "Cita no encontrada."`.
   - Agendar valida `requirePatientOfPhone(phone, patientId)`.
3. **Agendar**: `POST /api/v1/bot/appointments` `{phone, patientId, specialtyId, professionalId, startAt}` → `201`
   con `BotAppointmentResponse` (016a).
   - Usa `AppointmentScheduler.book` con `source=BOT`, `createdBy` = usuario `bot_whatsapp` y `reason` null.
   - La agenda ya valida el horario del profesional y el cruce.
4. **Límites (D-9)** en `BotBookingLimits`, sin Spring:
   - Entrada:
     - las citas activas (`PENDING`/`CONFIRMED`, futuras) de todos los pacientes activos del teléfono, obtenidas con
       `findActiveByPatient` por cada uno;
     - el paciente y la especialidad pedidos;
     - `maxActivePerPhone`.
   - Solo cuentan las citas con `source=BOT`.
   - Hay límite si el paciente ya tiene una cita activa del bot en esa especialidad, o si el teléfono ya suma
     `maxActivePerPhone` citas activas del bot → `409 BOT_LIMIT_REACHED`.
   - Recepción no tiene límites: la regla solo se aplica en este endpoint.
5. **Reprogramar**: `POST /api/v1/bot/appointments/{id}/reschedule` `{phone, startAt}` → `200` con la cita nueva.
   - Usa `AppointmentScheduler.reschedule(id, startAt, botUserId)`, con el mismo profesional y especialidad.
   - La nueva conserva el `source` de la original, que es el comportamiento actual de la agenda.
   - Si faltan menos de `changeMinHours` para el inicio de la original → `409 BOT_TOO_LATE`.
6. **Cancelar**: `POST /api/v1/bot/appointments/{id}/cancel` `{phone}` → `200`. Aplica la misma regla de 2 h.
7. **Confirmar**: `POST /api/v1/bot/appointments/{id}/confirm` `{phone}` → `200`. Solo desde `PENDING`. Si ya está
   `CONFIRMED`, responde `200` sin cambios, porque el paciente puede pulsar dos veces.
8. La hora sale de `ClinicClock`. No se toca `AppointmentService` salvo para que propague el `code` del cruce.

## Tests requeridos
**Unit** (`BotBookingLimitsTest`, 3 casos):
1. `check_patientHasBotAppointmentInSpecialty_limitReached`
2. `check_otherSpecialtyBelowPhoneMax_allowed`
3. `check_phoneAtMaxCountingOnlyBotSource_limitReached`. Debe incluir una cita `RECEPTION` que no cuenta.

**Integration** (`BotAppointmentIT`, 3 casos)
- Setup:
  - horario de `odontologa` para mañana y pasado, creado por la API de personal;
  - un paciente con teléfono conocido;
  - `MutableClock` restablecido en `@AfterEach`.
- Casos:
  1. `book_thenSecondInSameSpecialty_secondIs409BotLimitReached`:
     - la primera reserva → 201, `PENDING`, `source=BOT`;
     - la segunda, en la misma especialidad → 409 con `code=BOT_LIMIT_REACHED`.
  2. `book_phoneNotOwningPatient_returns404`.
  3. `cancel_lessThanTwoHoursBefore_returns409BotTooLate`:
     - se reserva para mañana 10:00;
     - se fija el reloj a mañana 08:30;
     - `cancel` → 409 con `code=BOT_TOO_LATE` y la cita sigue `PENDING`.

## Resultado
- **Qué se hizo:**
  - `src/main/resources/application.yml`: se agregaron `application.bot.limits.max-active-per-phone: 3` y `application.bot.limits.change-min-hours: 2`.
  - `config/BotProperties.java`: se agregó el record anidado `Limits(int maxActivePerPhone, int changeMinHours)` sin defaults redundantes en código.
  - `shared/response/ApiResponse.java`: se agregó el atributo opcional `code` y el factory `error(String message, String code)`.
  - `shared/exception/BusinessException.java`: se agregó el campo `code`, constructor `(message, code)` y método `getCode()`.
  - `shared/exception/GlobalExceptionHandler.java`: se actualizó el manejador de `BusinessException` para emitir `ApiResponse.error(ex.getMessage(), ex.getCode())`.
  - `appointments/domain/AppointmentOverlapException.java`: pasa el código estable `"APPOINTMENT_OVERLAP"`.
  - `bot/domain/BotBookingLimits.java`: regla pura D-9 sin Spring para validación de límites de citas por especialidad y máximo por teléfono contando solo citas con `source=BOT`.
  - `bot/api/dto/`: `BotBookRequest`, `BotRescheduleRequest`, `BotPhoneRequest`.
  - `bot/application/BotBookingService.java`: orquesta reservas, reprogramaciones, cancelaciones y confirmaciones. Se corrigió para obtener el ID de usuario mediante `CurrentUser.userId()` (autenticado como `bot_whatsapp` por el filtro), eliminando la dependencia y consulta a `UserRepository`, y consumiendo directamente los límites inyectados desde `BotProperties` sin fallbacks silenciosos.
  - `bot/api/BotAgendaController.java`: endpoints `POST /appointments` (201), `POST /appointments/{id}/reschedule` (200), `POST /appointments/{id}/cancel` (200) y `POST /appointments/{id}/confirm` (200).
  - `src/test/java/com/bellidental/bot/domain/BotBookingLimitsTest.java`: 3 tests unitarios para regla de límites D-9.
  - `src/test/java/com/bellidental/bot/BotAppointmentIT.java`: 3 tests de integración. Se corrigió `@AfterEach` para cancelar las citas activas creadas durante el test a través de la API de personal (`POST /api/v1/appointments/{id}/cancel` con headers de secretaria), eliminando `AppointmentRepository` y garantizando el aislamiento de cruces sin borrado directo de base de datos.

- **Resultado de los tests:**
  - Surefire: 92 tests ejecutados, 0 fallos, 0 errores, 0 omitidos (`BotBookingLimitsTest`: 3 tests OK).
  - Failsafe: 55 tests ejecutados, 0 fallos, 0 errores, 0 omitidos (`BotAppointmentIT`: 3 tests OK).
  - Total: 147 tests pasando exitosamente.

- **Problemas encontrados y supuestos tomados:**
  - Siguiendo la revisión, se reemplazó la limpieza directa con repositorio en `BotAppointmentIT` por cancelaciones mediante la API de citas con credenciales de `secretaria`. Al quedar en estado `CANCELLED`, las citas dejan de computar en la restricción de exclusión de Postgres `appointments_no_overlap`, permitiendo que tests posteriores como `EncounterFlowIT` se ejecuten sin conflictos.
  - El usuario del bot se obtiene dinámicamente desde el contexto de seguridad con `CurrentUser.userId()`, respetando el aislamiento modular sin inyectar repositorios de identidad.

## Revisión
Estado: APROBADO

Correcciones de la primera revisión, verificadas:
1. `BotAppointmentIT` ya no borra: en `@AfterEach` restablece el reloj y luego cancela, por la API de `secretaria`,
   las citas activas del paciente del test. Ya no inyecta `AppointmentRepository`. Los repositorios que quedan
   (`UserRepository`, `ProfessionalRepository`) solo leen el profesional sembrado.
2. `BotBookingService` usa `CurrentUser.userId()`; se quitó `UserRepository`.
3. Se quitaron los valores por defecto del código: los límites vienen solo de `BotProperties`.

`./mvnw -q clean verify` corrido por Claude: surefire 92/0/0/0, failsafe 55/0/0/0.
