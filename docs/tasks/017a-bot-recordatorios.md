# 017a — Bot (Java): recordatorios

## Objetivo
Recordatorio del día anterior (RF13).
- Java crea el recordatorio cuando se agenda una cita, por cualquier vía, y lo anula si la cita deja de estar activa.
- Expone al agente:
  - los recordatorios vencidos;
  - el registro del envío;
  - la respuesta del paciente a los botones *Confirmar / Cancelar / Reprogramar*.
- La respuesta siempre se guarda (D-2).

Plan §4.11. El envío por WhatsApp es la 017b.

**Estructura**: amplía `bot`. Escucha `AppointmentEvent`, un record público de la raíz de `appointments`. Usa
`AppointmentScheduler`, `PatientDirectory`, `ProfessionalDirectory` y `PrivacyConsentRepository` (015a).
`appointments` no conoce a `bot`.

## Archivos a tocar
```
shared/enums/
  ReminderStatus.java                       (nuevo) PENDING, SENT, FAILED, RESPONDED, CANCELLED
  ReminderResponse.java                     (nuevo) CONFIRMED, CANCELLED, RESCHEDULED
persistence/
  entity/AppointmentReminder.java           (nuevo) tabla appointment_reminders de V13 (kind = "DAY_BEFORE")
  repository/AppointmentReminderRepository.java   (nuevo)
bot/
  api/BotReminderController.java            (nuevo)
  api/dto/                                  + DueReminderResponse, ReminderSentRequest, ReminderReplyRequest,
                                              ReminderReplyResponse
  application/AppointmentReminderListener.java   (nuevo) @EventListener de AppointmentEvent
  application/BotReminderService.java       (nuevo) vencidos, envío y respuesta
src/test/java/com/bellidental/bot/BotReminderIT.java   (nuevo)
```

## Criterios de aceptación
1. **Creación y anulación** (`@EventListener`, síncrono, en la misma transacción que la cita):
   - Si el evento trae `previousAppointmentId` (reprogramación), sus recordatorios `PENDING` pasan a `CANCELLED`.
   - Evento `CREATED` o `RESCHEDULED` con la cita `PENDING`/`CONFIRMED`:
     - se crea `DAY_BEFORE` con `scheduled_at` = día anterior al inicio, a las 09:00 en `application.clinic.zone`;
     - no se crea si ese momento ya pasó o si la cita ya tiene uno `PENDING`.
   - Cualquier evento cuya cita quede fuera de `PENDING`/`CONFIRMED` (cancelada, no-show, atendida, reprogramada):
     sus recordatorios `PENDING` pasan a `CANCELLED`.
2. **Vencidos**: `GET /api/v1/bot/reminders/due` → hasta 50
   `[{reminderId, appointmentId, phone, patientFirstName, professionalName, startAt}]`. Condiciones:
   - el recordatorio está `PENDING` y `scheduled_at ≤ now`;
   - la cita sigue activa y su `start_at > now`;
   - el paciente tiene teléfono y existe un consentimiento de ese teléfono con la versión vigente del aviso.
     Sin consentimiento no se envía nada (RF10).
   - Solo esos datos: nada clínico (RNF03).
3. **Envío**:
   - `POST /api/v1/bot/reminders/{id}/sent` `{waMessageId}` → `SENT`, `sent_at`, `wa_message_id`.
   - `POST /api/v1/bot/reminders/{id}/failed` → `FAILED`.
   - Ambos solo desde `PENDING`; si no → 409.
4. **Respuesta**: `POST /api/v1/bot/reminders/response` `{waMessageId, phone, response}` →
   `{outcome, appointmentId, patientId}`.
   - Se busca el recordatorio por `wa_message_id`. Debe estar `SENT` y su paciente debe tener ese teléfono; si no
     → 404.
   - Siempre se guardan `RESPONDED`, `response` y `responded_at` (D-2).
   - `CONFIRMED` → confirma la cita (si ya está `CONFIRMED`, no la cambia) → `outcome=CONFIRMED`.
   - `CANCELLED` → cancela la cita → `outcome=CANCELLED`.
   - `RESCHEDULED` → no cambia la cita (el agente abre su flujo de reprogramar) →
     `outcome=RESCHEDULE_REQUESTED`.
   - Si faltan menos de `application.bot.limits.change-min-hours` para la cita y la respuesta es `CANCELLED` o
     `RESCHEDULED` → la cita no cambia, `outcome=TOO_LATE` y `200`. No es un 409, para que no se pierda la
     respuesta guardada.
5. La hora sale de `ClinicClock`. `ApiResponse` en todo.

## Tests requeridos
**Unit**: ninguno.

**Integration** (`BotReminderIT`, 3 casos)
- Setup:
  - horario de `odontologa` para pasado mañana;
  - paciente con teléfono y consentimiento aceptado por `POST /api/v1/bot/consents`;
  - cita para pasado mañana 10:00 creada por la API de personal (recepción);
  - `MutableClock` restablecido en `@AfterEach`.
- Casos:
  1. `due_dayBeforeAt0905_thenSentAndConfirmed_appointmentConfirmed`:
     - con el reloj en mañana 09:05, `due` contiene la cita;
     - `sent` con `wamid.test1`;
     - `response` `CONFIRMED` → `outcome=CONFIRMED`;
     - la cita está `CONFIRMED` (API de personal) y `due` ya no la lista.
  2. `cancelAppointment_beforeDue_reminderNotListed`: se cancela la cita por la API de personal; con el reloj en
     mañana 09:05, `due` no la contiene.
  3. `response_cancelLessThanTwoHoursBefore_tooLateAndAppointmentUnchanged`:
     - se marca como enviado;
     - se fija el reloj a pasado mañana 08:30;
     - `CANCELLED` → `outcome=TOO_LATE` y la cita sigue `PENDING`.

## Resultado
- **Qué se hizo:**
  - `shared/enums/ReminderStatus.java` (nuevo): enum con estados `PENDING`, `SENT`, `FAILED`, `RESPONDED`, `CANCELLED`.
  - `shared/enums/ReminderResponse.java` (nuevo): enum con opciones `CONFIRMED`, `CANCELLED`, `RESCHEDULED`.
  - `persistence/entity/AppointmentReminder.java` (nuevo): entidad JPA para la tabla `appointment_reminders` (V13) con `kind = "DAY_BEFORE"`.
  - `persistence/repository/AppointmentReminderRepository.java` (nuevo): repositorio con búsquedas por appointment, `waMessageId`, verificación de pendientes existentes y recordatorios vencidos.
  - `bot/api/dto/` (nuevos): `DueReminderResponse`, `ReminderSentRequest`, `ReminderReplyRequest`, `ReminderReplyResponse`.
  - `bot/application/AppointmentReminderListener.java` (nuevo): `@EventListener` síncrono para `AppointmentEvent`. Si hay `previousAppointmentId` cancela sus recordatorios pendientes; si la cita queda activa programa recordatorio `DAY_BEFORE` para el día previo a las 09:00 (si es futuro y no existe ya); si la cita sale de activa pasa los recordatorios pendientes a `CANCELLED`.
  - `bot/application/BotReminderService.java` (nuevo): servicio de consulta de vencidos (máx 50, verificando citas activas futuras y consentimiento vigente para el teléfono en E.164), marcado de envío (`SENT`, `FAILED`), y gestión de respuestas registrando `RESPONDED` con `responded_at` (D-2) y aplicando la regla de antelación de 2h (`TOO_LATE` sin 409 si es tardía).
  - `bot/api/BotReminderController.java` (nuevo): endpoints `GET /api/v1/bot/reminders/due`, `POST /api/v1/bot/reminders/{id}/sent`, `POST /api/v1/bot/reminders/{id}/failed` y `POST /api/v1/bot/reminders/response`.
  - `src/test/java/com/bellidental/bot/BotReminderIT.java` (nuevo): 3 tests de integración (vencidos a las 09:05 del día anterior con envío y confirmación actualizando la cita, anulación de recordatorio al cancelar cita previa, y respuesta tardía con `outcome=TOO_LATE` preservando la cita en `PENDING`).

- **Resultado de los tests:**
  - Surefire: 92 tests ejecutados, 0 fallos, 0 errores, 0 omitidos.
  - Failsafe: 58 tests ejecutados, 0 fallos, 0 errores, 0 omitidos (`BotReminderIT`: 3 tests OK).
  - Total: 150 tests pasando exitosamente.

- **Problemas encontrados y supuestos tomados:**
  - `PatientSummary` expone el teléfono normalizado como `phoneE164()`, el cual se utilizó para validar el consentimiento de privacidad en `PrivacyConsentRepository`.
  - Se utilizó un switch expression con `yield` en `BotReminderService.handleReply` para mapear de forma exhaustiva los outcomes (`CONFIRMED`, `CANCELLED`, `RESCHEDULE_REQUESTED` y `TOO_LATE`).
  - Siguiendo la convención de aislamiento, en `@AfterEach` de `BotReminderIT` se cancelan las citas activas creadas para el test mediante la API de personal (`POST /api/v1/appointments/{id}/cancel`).

## Revisión
Estado: APROBADO

1. **Listener:** es síncrono y corre en la transacción de la cita.
   - Anula los recordatorios de la cita anterior en una reprogramación.
   - Crea `DAY_BEFORE` a las 09:00 del día anterior en la zona de la clínica, solo si ese momento es futuro y la cita
     no tiene ya uno pendiente.
   - Si la cita sale de `PENDING`/`CONFIRMED`, sus pendientes pasan a `CANCELLED`.
2. **Vencidos:** cita activa y futura, paciente con teléfono, consentimiento de la versión vigente y máximo 50. Solo
   devuelve el primer nombre, el profesional y la hora; nada clínico.
3. **`sent`/`failed`:** solo desde `PENDING`.
4. **Respuesta:** se busca por `wa_message_id` y se valida el teléfono. Siempre se guarda `RESPONDED` (D-2), y
   `TOO_LATE` responde 200 sin cambiar la cita.
5. `./mvnw -q clean verify` corrido por Claude: surefire 92/0/0/0, failsafe 58/0/0/0.
6. Observaciones, aceptables en el MVP y sin corrección:
   - **Recordatorios que nunca se envían:** los de pacientes sin consentimiento o sin teléfono quedan `PENDING` para
     siempre. `due` los vuelve a cargar y descartar cada 5 minutos, con varias consultas por cada uno. Con el volumen
     de la clínica no pesa. Si crece, se puede limitar la consulta a recordatorios de citas futuras o marcarlos
     `CANCELLED` al pasar la cita.
   - **Respuesta a una cita ya cancelada:** si recepción la canceló después del envío y el paciente pulsa
     *Confirmar*, la agenda responde 409 y se pierde también la respuesta guardada. El agente deriva, que es una
     salida aceptable.
