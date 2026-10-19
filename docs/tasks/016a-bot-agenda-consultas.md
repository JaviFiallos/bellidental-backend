# 016a — Bot (Java): consultas de agenda

## Objetivo
Endpoints de solo lectura que el agente necesita para agendar (RF12), reprogramar (RF15) y atender urgencias (D-21):
especialidades, profesionales, días y horas libres, huecos urgentes y las citas activas del paciente. Plan §4.11.
No escribe nada; las escrituras van en la 016b.

**Estructura**: amplía `bot` (015a). Depende de `AppointmentScheduler`, `ProfessionalDirectory` y `PatientDirectory`
(este último a través de `BotIdentityService`). Añade lo mínimo a las interfaces públicas de `appointments` y
`professionals`.

## Archivos a tocar
```
src/main/resources/application.yml           + application.bot.urgent-specialty-id: 1
config/BotProperties.java                     + urgentSpecialtyId (Long)
professionals/
  ProfessionalDirectory.java                  + List<SpecialtySummary> findActiveSpecialties()
                                              + List<ProfessionalSummary> findActiveBySpecialty(Long specialtyId)
  ProfessionalDirectoryImpl.java              los implementa
persistence/repository/
  ProfessionalSpecialtyRepository.java        + findBySpecialtyIdAndActiveTrue(Long specialtyId)
  AppointmentRepository.java                  + consulta de citas activas futuras de un paciente
appointments/
  AppointmentScheduler.java                   + List<AppointmentSummary> findActiveByPatient(UUID patientId)
  application/AppointmentSchedulerImpl.java   lo implementa
bot/
  api/BotAgendaController.java                (nuevo)
  api/dto/                                    + BotSpecialtyResponse, BotProfessionalResponse, BotSlotResponse,
                                                UrgentSlotsResponse, BotAppointmentResponse
  application/BotAvailabilityService.java     (nuevo) días, horas y huecos urgentes
  application/BotAppointmentQueryService.java (nuevo) citas activas del paciente
src/test/java/com/bellidental/bot/BotAvailabilityIT.java   (nuevo)
```

## Criterios de aceptación
1. `GET /api/v1/bot/specialties` → especialidades activas `[{specialtyId, name, durationMinutes}]`.
2. `GET /api/v1/bot/professionals?specialtyId=` → profesionales activos con esa especialidad activa
   `[{professionalId, displayName}]`. `displayName` = nombres + apellidos. Nada más del profesional.
3. **Huecos** (`BotSlotResponse` = `{professionalId, professionalName, startAt, endAt}`, ISO-8601 con offset):
   - Se calculan con `AppointmentScheduler.findAvailableSlots`, con la duración por defecto de la especialidad.
     El bot no elige la duración.
   - Si falta `professionalId`, se usan todos los profesionales activos de la especialidad ("el primero
     disponible"): se unen y se ordenan por `startAt`. Si varios tienen la misma hora, queda una sola opción.
   - `GET /api/v1/bot/availability/days?specialtyId&professionalId?` → fechas ISO (`"2026-10-20"`) de los próximos
     14 días (desde `clock.today()`, inclusive) que tienen al menos un hueco.
   - `GET /api/v1/bot/availability?specialtyId&professionalId?&date` → los primeros 10 huecos del día.
4. **Urgencias** (D-21): `GET /api/v1/bot/urgent-slots` → `{specialtyId, noSlotsToday, slots[]}`.
   - `specialtyId` = `application.bot.urgent-specialty-id`.
   - `slots` = los 3 primeros huecos de hoy o mañana, de cualquier profesional, con `startAt ≥ now + 30 min`.
   - `noSlotsToday = true` si ninguno es de hoy. El agente lo usa como "fuera de horario" para añadir el teléfono
     de la doctora.
5. **Citas del paciente**: `GET /api/v1/bot/appointments?phone&patientId` → `[{appointmentId, specialtyId,
   specialtyName, professionalId, professionalName, startAt, status}]`.
   - Incluye solo las citas activas: `PENDING`/`CONFIRMED` con `start_at > now`, ordenadas por `startAt`.
   - Primero `requirePatientOfPhone`; si el paciente no es de ese teléfono → `404`.
6. Toda hora sale de `ClinicClock`, sin `now()` sin reloj. Si la especialidad o el profesional no existen o están
   inactivos → `404`. `ApiResponse` en todo.

## Tests requeridos
**Unit**: ninguno (el cálculo de huecos ya tiene sus tests).

**Integration** (`BotAvailabilityIT`, 3 casos)
- Setup:
  - horario del profesional sembrado `odontologa` (especialidad 1) creado por la API de personal para mañana y
    pasado mañana;
  - `MutableClock` fijado a hoy 10:00 en la zona de la clínica y restablecido en `@AfterEach`;
  - `X-Bot-Key: it-bot-key`.
- Casos:
  1. `availability_withoutProfessional_listsDaysAndSlotsWithProfessionalName`:
     - `days` contiene mañana;
     - `availability` de mañana → 1..10 huecos ordenados con `professionalName`.
  2. `urgentSlots_noScheduleToday_returnsTomorrowAndFlagsNoSlotsToday`:
     - hay como mucho 3 huecos, todos de mañana;
     - `noSlotsToday=true`.
  3. `appointments_phoneNotOwningPatient_returns404`.

## Resultado
- **Archivos creados o modificados:**
  - `src/main/resources/application.yml`: se agregó `application.bot.urgent-specialty-id: 1`.
  - `config/BotProperties.java`: se agregó el campo `urgentSpecialtyId` (Long).
  - `persistence/repository/ProfessionalSpecialtyRepository.java`: se agregó la consulta `findBySpecialtyIdAndActiveTrue(Long specialtyId)`.
  - `professionals/ProfessionalDirectory.java`: se agregaron `findActiveSpecialties()` y `findActiveBySpecialty(Long specialtyId)`.
  - `professionals/ProfessionalDirectoryImpl.java`: implementación de `findActiveSpecialties()` y `findActiveBySpecialty(Long specialtyId)` filtrando profesionales activos no eliminados.
  - `persistence/repository/AppointmentRepository.java`: se agregó `findActiveFutureByPatient` (citas `PENDING`/`CONFIRMED` con `startAt > now` ordenadas por `startAt`).
  - `appointments/AppointmentScheduler.java`: se agregó `List<AppointmentSummary> findActiveByPatient(UUID patientId)`.
  - `appointments/application/AppointmentSchedulerImpl.java`: implementación de `findActiveByPatient` usando `ClinicClock` para el instante actual.
  - `bot/api/dto/` (nuevos): `BotSpecialtyResponse`, `BotProfessionalResponse`, `BotSlotResponse`, `UrgentSlotsResponse`, `BotAppointmentResponse`.
  - `bot/application/BotAvailabilityService.java` (nuevo): consulta de especialidades activas, profesionales por especialidad, días con huecos disponibles (próximos 14 días desde hoy), cálculo de huecos con deduplicación por hora de inicio si falta el profesional, y búsqueda de huecos para urgencias (hoy y mañana con inicio >= now + 30 min, máx 3 huecos y bandera `noSlotsToday`).
  - `bot/application/BotAppointmentQueryService.java` (nuevo): consulta de citas activas futuras del paciente validando la pertenencia previa del paciente al teléfono con `BotIdentityService.requirePatientOfPhone`.
  - `bot/api/BotAgendaController.java` (nuevo): endpoints `/api/v1/bot/specialties`, `/api/v1/bot/professionals`, `/api/v1/bot/availability/days`, `/api/v1/bot/availability`, `/api/v1/bot/urgent-slots`, `/api/v1/bot/appointments`.
  - `src/test/java/com/bellidental/bot/BotAvailabilityIT.java` (nuevo): 3 tests de integración con `MutableClock` fijado a las 10:00 (disponibilidad de días y huecos ordenados con nombre de profesional, huecos urgentes sin horario hoy devolviendo mañana con `noSlotsToday=true`, y rechazo 404 para paciente no asociado al teléfono).

- **Resultado de los tests:**
  - Surefire: 89 tests ejecutados, 0 fallos, 0 errores, 0 omitidos.
  - Failsafe: 52 tests ejecutados, 0 fallos, 0 errores, 0 omitidos (`BotAvailabilityIT`: 3 tests OK).
  - Total: 141 tests pasando exitosamente.

- **Problemas encontrados y supuestos tomados:**
  - El cálculo de huecos disponibles reutiliza `AppointmentScheduler.findAvailableSlots`, deduplicando huecos concurrentes por `startAt` cuando se consulta sin profesional específico para entregar una sola opción por franja.
  - En urgencias, se evaluaron hoy y mañana filtrando citas con al menos 30 minutos de antelación respecto a `clinicClock.now()`, limitando el resultado a los 3 primeros y seteando `noSlotsToday=true` si ninguno corresponde a la fecha de hoy.
  - Toda consulta temporal utiliza `ClinicClock` para evitar dependencias del reloj del sistema.

## Revisión
Estado: APROBADO

1. `bot` solo usa `AppointmentScheduler`, `ProfessionalDirectory` y `BotIdentityService`. A las interfaces públicas
   se añadió lo mínimo: `findActiveByPatient`, `findActiveSpecialties` y `findActiveBySpecialty`.
2. Huecos: se calculan con la duración por defecto de la especialidad. "Primero disponible" une los huecos de todos
   los profesionales, los ordena y deja uno por hora. Días: los próximos 14 desde `clock.today()`. Horas: máximo 10.
3. Urgencias: hoy y mañana, con inicio a partir de `now + 30 min`, máximo 3. `noSlotsToday` compara la fecha local,
   lo cual es correcto porque `SlotCalculator` genera las horas con la zona de la clínica.
4. Citas del paciente: primero se valida la pertenencia al teléfono (si no, 404). Solo se devuelven
   `PENDING`/`CONFIRMED` futuras, calculadas con `ClinicClock`.
5. `./mvnw -q clean verify` corrido por Claude (el mismo que en la 015a): surefire 89/0/0/0, failsafe 52/0/0/0.
6. Observación: `ProfessionalDirectoryImpl.findActiveBySpecialty` navega `ProfessionalSpecialty::getProfessional`
   sin `@Transactional`. Por HTTP funciona, porque la sesión sigue abierta durante la petición (open-in-view). Si en
   el futuro se llama fuera de una petición (un job o un listener), hay que añadirle `@Transactional(readOnly = true)`.
