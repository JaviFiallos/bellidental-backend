# 020 — Estadísticas para la clínica (dashboard)

## Objetivo
**Requerimiento nuevo del usuario**: estadísticas importantes para el negocio en `com.bellidental.dashboard`
(la carpeta ya existe vacía). Un único endpoint de solo lectura `GET /api/v1/dashboard/summary?from=&to=` (fechas
`LocalDate` en la zona de la clínica; por defecto el mes actual: del día 1 a hoy según `clinicClock.today()`).
Pragmático: consultas agregadas. No hay migración. **Depende de la 012** (tabla `payments`).

Decisiones (anotadas en el plan §6, «Confirmadas»):
- **D-25**: el dashboard **no** consulta módulo por módulo vía `Directory` (traería listas enteras a memoria y
  obligaría a ampliar cinco interfaces). Usa un **repositorio de solo lectura con consultas nativas agregadas**
  (`persistence/repository/DashboardRepository`), en la línea de D-15. Costo: la regla de saldo vive también en
  SQL; el IT comprueba que coincide con `GET /patients/{id}/account` (012).
- **D-26**: los ingresos son sensibles → el endpoint es solo `hasRole('ADMIN')`. El permiso `DASHBOARD_VIEW` del
  odontólogo queda sin uso (no se toca la semilla).

## Archivos a tocar
```
src/main/java/com/bellidental/
  persistence/repository/DashboardRepository.java   (nuevo) clase @Repository con EntityManager inyectado y consultas
                                                    nativas (sin entidad, solo lectura)
  dashboard/
    api/DashboardController.java
    api/dto/
      DashboardSummaryResponse.java   from, to, patients, appointments, billing, topTreatments, byProfessional, byChannel
      PatientStats.java               totalActive, newInRange
      AppointmentStats.java           total, byStatus (Map<String,Long>), noShowRate (BigDecimal 0–100, 1 decimal),
                                      completedEncounters
      BillingStats.java               collected, byMethod (Map<String,BigDecimal>), outstandingBalance
      TopTreatmentResponse.java       code, name, quantity, amount
      ProfessionalStats.java          professionalId, name, encounters, revenue
      ChannelStat.java                source, appointments
    application/DashboardService.java
    domain/NoShowRate.java            pura: of(long noShows, long pastAppointments) → BigDecimal
src/test/java/com/bellidental/dashboard/
  domain/NoShowRateTest.java          (nuevo)
  DashboardSummaryIT.java             (nuevo)
docs/IMPLEMENTATION-PLAN.md           §6 «Pendientes»: D-25, D-26 (la fila 020 de §3 ya existe)
```

## Criterios de aceptación
1. `GET /api/v1/dashboard/summary` (`@PreAuthorize("hasRole('ADMIN')")`), `ApiResponse<DashboardSummaryResponse>`.
   `@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, to`. Ambos o
   ninguno (uno solo → 400 `InvalidInputException`); `from > to` → 400; más de 366 días → 400. Rango inclusivo
   convertido a instantes: `start = from.atStartOfDay(clinicClock.zone()).toOffsetDateTime()`,
   `end = to.plusDays(1).atStartOfDay(clinicClock.zone()).toOffsetDateTime()`; todas las consultas usan
   `columna >= :start AND columna < :end`.
2. `DashboardRepository`, un método por dato (parámetros `OffsetDateTime start, end`). Ojo con el esquema real:
   **`encounters` no tiene `patient_id`** (se llega al paciente por `appointments.patient_id` vía
   `encounters.appointment_id`), y `appointments` **no** tiene `voided_at`. Excluir anulados (`voided_at IS NULL`)
   en `encounters`, `performed_treatments` y `payments`.
   - `long countActivePatients()` → `patients` con `active = true AND deleted_at IS NULL`.
   - `long countNewPatients(start, end)` → `patients.created_at` en rango y `deleted_at IS NULL`.
   - `Map<String, Long> appointmentsByStatus(start, end)` → `status, COUNT(*)` por `appointments.start_at` en rango.
   - `long countNoShows(start, end)` → `status = 'NO_SHOW'`, `start_at` en rango.
   - `long countPastAppointments(start, end, OffsetDateTime now)` → `start_at` en rango, `start_at < :now` y
     `status NOT IN ('CANCELLED','RESCHEDULED')`.
   - `long countCompletedEncounters(start, end)` → `encounters` con `status = 'COMPLETED'`, `voided_at IS NULL`,
     `closed_at` en rango.
   - `BigDecimal collectedTotal(start, end)` y `Map<String, BigDecimal> collectedByMethod(start, end)` →
     `payments` con `voided_at IS NULL` y `paid_at` en rango.
   - `BigDecimal outstandingBalance()` (todo el histórico), exactamente:
     ```sql
     SELECT COALESCE(SUM(balance), 0) FROM (
       SELECT patient_id, SUM(amount) AS balance FROM (
         SELECT a.patient_id, pt.price * pt.quantity AS amount
           FROM performed_treatments pt
           JOIN encounters e   ON e.encounter_id = pt.encounter_id
           JOIN appointments a ON a.appointment_id = e.appointment_id
          WHERE pt.voided_at IS NULL AND e.voided_at IS NULL
         UNION ALL
         SELECT p.patient_id, -p.amount FROM payments p WHERE p.voided_at IS NULL
       ) movements GROUP BY patient_id HAVING SUM(amount) > 0
     ) balances
     ```
   - `List<Object[]> topTreatments(start, end)` → `performed_treatments` no anulados de `encounters` no anuladas con
     `encounters.start_at` en rango, agrupado por `treatment_catalog.code, name` (join por `treatment_id`):
     `SUM(quantity)`, `SUM(price * quantity)`, orden `SUM(quantity) DESC, SUM(price * quantity) DESC`, `LIMIT 5`.
   - `List<Object[]> encountersAndRevenueByProfessional(start, end)` → `encounters` COMPLETED no anuladas con
     `closed_at` en rango, por `professional_id`, nombre `first_name || ' ' || last_name` (join `professionals`),
     `COUNT(DISTINCT e.encounter_id)` y `COALESCE(SUM(pt.price * pt.quantity), 0)` con `LEFT JOIN
     performed_treatments pt ON pt.encounter_id = e.encounter_id AND pt.voided_at IS NULL`.
   - `Map<String, Long> appointmentsBySource(start, end)` → `source, COUNT(*)`.
   Conversión de resultados: conteos con `((Number) valor).longValue()`; montos con
   `new BigDecimal(valor.toString()).setScale(2, RoundingMode.HALF_UP)`. Nada de reglas de negocio en SQL salvo
   agregar.
3. `DashboardService` (`@Transactional(readOnly = true)`) arma la respuesta; `total` de citas = suma de
   `byStatus`; `noShowRate = NoShowRate.of(countNoShows, countPastAppointments)` con `now = clinicClock.now()`.
   Mapas (`LinkedHashMap`) con todas las claves aunque valgan 0, en este orden: estados `PENDING, CONFIRMED,
   ATTENDED, CANCELLED, NO_SHOW, RESCHEDULED`; métodos `EFECTIVO, TRANSFERENCIA, TARJETA`; `byChannel` es una
   lista de `ChannelStat` con `RECEPTION, PROFESSIONAL, BOT, WALK_IN` (usar los `values()` de `AppointmentStatus`,
   `PaymentMethod` y `AppointmentSource`).
4. `NoShowRate.of`: `noShows * 100 / past`, escala 1, `HALF_UP`; `past == 0` → `BigDecimal.ZERO.setScale(1)`;
   `noShows > past` o negativos → `InvalidInputException`.
5. Montos `BigDecimal` con escala 2.

## Tests requeridos
**Unit** (`NoShowRateTest`, 3 casos):
- `of_twoOfEight_returns25Point0`
- `of_noPastAppointments_returnsZero`
- `of_moreNoShowsThanPast_throwsInvalidInput`

**Integration** (`DashboardSummaryIT`, 2 casos). Importante: paciente nuevo, atención (`start_at`, `closed_at`) y
pago (`paid_at`) se fechan **ahora**, no el día de la cita; y la base es compartida con otros IT. Por eso el caso
usa el rango de **hoy** (`today = LocalDate.now(ZoneId.of("America/Guayaquil"))`, `from=to=today`) y compara
**diferencias** entre una lectura antes y otra después de crear sus datos.
- `summary_asAdminWithData_returnsExpectedFigures`: (1) `GET summary` como director → `before`. (2) Setup vía API
  como en `PaymentFlowIT`: paciente nuevo (sufijo aleatorio), cita con check-in en `LocalDate.now().plusDays(111)`
  (día propio), atención de la odontóloga, tratamiento con `price` 40.00 y `quantity` 2 (cargo 80.00),
  `complete`, abono de 30.00 en EFECTIVO con SECRETARIA. (3) `GET summary` → `after`. Aserciones (after − before):
  `patients.newInRange` +1, `appointments.completedEncounters` +1, `billing.collected` +30.00,
  `billing.byMethod.EFECTIVO` +30.00, `billing.outstandingBalance` +50.00; en `byProfessional`, la entrada de la
  odontóloga: `encounters` +1 y `revenue` +80.00. Además `GET /patients/{id}/account` → `balance` 50.00
  (coherencia con D-25). Comparar montos con `isEqualByComparingTo`.
- `summary_asSecretaria_returnsForbidden`: 403.

## Decisiones (confirmadas por el usuario el 2026-10-21)
- **D-25**: agregados con consultas nativas en un repositorio de solo lectura en vez de Directories; se exponen por el único endpoint `GET /dashboard/summary`.
- **D-26**: este dashboard (con dinero) es solo para ADMIN; `DASHBOARD_VIEW` del odontólogo queda sin uso aquí. El odontólogo tendrá una vista propia sin dinero (D-26b, sin tarea por ahora).
- «Tasa de inasistencia» = NO_SHOW / citas pasadas no canceladas ni reprogramadas.
- Ingresos = pagos no anulados por `paid_at`; atenciones «completadas» por `closed_at`; tratamientos más
  frecuentes por fecha de la atención.
- Lista de estadísticas del requerimiento; se pueden añadir otras si el usuario las pide.

## Resultado

**Archivos creados**
- `persistence/repository/DashboardRepository.java` (`@Repository`, EntityManager, consultas nativas de solo lectura; `outstandingBalance` con el SQL de la tarea).
- `dashboard/api/DashboardController.java`; `dashboard/api/dto/` (DashboardSummaryResponse, PatientStats, AppointmentStats, BillingStats, TopTreatmentResponse, ProfessionalStats, ChannelStat).
- `dashboard/application/DashboardService.java` (valida rango, por defecto mes actual; mapas con todas las claves en orden).
- `dashboard/domain/NoShowRate.java` (pura; `of` lanza `InvalidInputException` si inasistencias > pasadas o negativos, y comprueba eso antes que `past == 0`).
- Tests: `src/test/java/com/bellidental/dashboard/domain/NoShowRateTest.java` (3), `src/test/java/com/bellidental/dashboard/DashboardSummaryIT.java` (2).

**Archivos modificados**
- `docs/IMPLEMENTATION-PLAN.md`: §6 filas D-25 y D-26 (provisionales).
- No hay migración ni cambios en la semilla.

**Tests (resumen real)**
- Surefire: 100 tests, 0 fallos, 0 errores, 0 omitidos (NoShowRateTest 3/3).
- Failsafe: 69 tests, 0 fallos, 0 errores, 0 omitidos (DashboardSummaryIT 2/2).
- `./mvnw -q clean verify`: exit 0.

**Problemas y supuestos**
- Varios IT del bot dejan el `MutableClock` compartido fijado a otra fecha (`clock.fix` sin `reset`). `DashboardSummaryIT` llama a `clock.reset()` en `setUp` para que `closed_at` y `paid_at` caigan en hoy. Causa preexistente de aislamiento entre tests, no se tocó en los bot.
- El paciente se crea dentro del test, después de la lectura «antes»; si se creaba en `setUp`, contaba en el `before` y `newInRange` no subía.
- No se hizo commit.

## Revisión
Estado: APROBADO

- `./mvnw -q clean verify` exit 0. Surefire 100/0/0/0, failsafe 69/0/0/0 (incluye NoShowRateTest 3 y
  DashboardSummaryIT 2).
- Corrección mínima hecha por el revisor: `countNoShows` ahora recibe `now` y filtra `start_at < :now`.
  `AppointmentTransitions.noShow` permite marcar NO_SHOW en una cita futura; sin el filtro, `noShows > past` hacía
  que `NoShowRate.of` lanzara `InvalidInputException` y el endpoint respondiera 400 con datos reales.
- SQL revisado: el saldo pendiente usa el de la tarea; los rangos son `[from 00:00, to+1 00:00)` en
  `clinicClock.zone()`; no se usa `patient_id` en encounters ni `voided_at` en appointments.
- `clock.reset()` en el `setUp` del IT no rompe el contexto: deja el reloj en tiempo real (su estado por defecto), y
  los IT que necesitan fijarlo llaman a `fix` y `reset` ellos mismos. Que algunos IT del bot no reseteen es una
  deuda previa, fuera de esta tarea.
