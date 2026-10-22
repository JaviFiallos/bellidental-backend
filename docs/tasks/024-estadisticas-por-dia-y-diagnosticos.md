# 024 — Estadísticas: serie por día, top 3 tratamientos y top 3 diagnósticos

## Objetivo
Ampliar `GET /api/v1/dashboard/summary` (tarea 020) con lo que pidió el usuario (2026-10-22):
1. **Serie por día** (`byDay`): por cada día del rango, citas, pacientes atendidos (atenciones completadas),
   pacientes nuevos e ingresos. Hoy solo hay totales del período.
2. **Top 3 diagnósticos CIE-10** (`topDiagnoses`) en el rango.
3. **Top de tratamientos: de 5 a 3** (`LIMIT 3`).

Solo consultas agregadas nuevas en `DashboardRepository` (D-25). **Sin migración** ni cambios de esquema.

## Archivos a tocar
```
src/main/java/com/bellidental/
  persistence/repository/DashboardRepository.java       (modif.) topTreatments LIMIT 3; 5 métodos nuevos
  dashboard/
    api/dto/DashboardSummaryResponse.java               (modif.) añadir al final: topDiagnoses, byDay
    api/dto/TopDiagnosisResponse.java                   (nuevo) code, description, occurrences, patients
    api/dto/DailyStat.java                              (nuevo) date, appointments, completedEncounters,
                                                        newPatients, collected
    application/DashboardService.java                   (modif.) arma byDay con todos los días y topDiagnoses
src/test/java/com/bellidental/dashboard/DashboardSummaryIT.java   (modif.) aserciones nuevas en el caso principal
docs/IMPLEMENTATION-PLAN.md                             (sin cambios)
```

## Criterios de aceptación
1. `DailyStat(LocalDate date, long appointments, long completedEncounters, long newPatients, BigDecimal collected)`.
   `TopDiagnosisResponse(String code, String description, long occurrences, long patients)`.
   `DashboardSummaryResponse` añade **al final** `List<TopDiagnosisResponse> topDiagnoses, List<DailyStat> byDay`
   (no reordenar los campos existentes).
2. El día se calcula en la zona de la clínica: en SQL `CAST((<columna> AT TIME ZONE :zone) AS date)` con
   `zone = clinicClock.zone().getId()` (p. ej. `America/Guayaquil`), y los rangos siguen siendo
   `columna >= :start AND columna < :end` (los mismos `start`/`end` de la 020). Cada método devuelve
   `Map<LocalDate, …>` (convertir `java.sql.Date` con `toLocalDate()`):
   - `appointmentsByDay(start, end, zone)` → `appointments.start_at`, `status NOT IN ('CANCELLED','RESCHEDULED')`;
   - `completedEncountersByDay(start, end, zone)` → `encounters` `status = 'COMPLETED'`, `voided_at IS NULL`, por
     `closed_at` (mismo criterio que `countCompletedEncounters`);
   - `newPatientsByDay(start, end, zone)` → `patients.created_at`, `deleted_at IS NULL`;
   - `collectedByDay(start, end, zone)` → `payments` `voided_at IS NULL`, por `paid_at`, `SUM(amount)`.
3. `DashboardService`: `byDay` tiene **una entrada por cada día de `from` a `to`** (inclusive, orden ascendente),
   con 0 / `0.00` cuando no hay datos (máximo 366 entradas por la validación existente).
4. `topDiagnoses(start, end)`:
   ```sql
   SELECT ic.code, ic.description, COUNT(*) AS occurrences, COUNT(DISTINCT a.patient_id) AS patients
     FROM encounter_diagnoses ed
     JOIN encounters e    ON e.encounter_id = ed.encounter_id
     JOIN appointments a  ON a.appointment_id = e.appointment_id
     JOIN icd10_codes ic  ON ic.icd10_id = ed.icd10_id
    WHERE ed.voided_at IS NULL AND e.voided_at IS NULL
      AND e.start_at >= :start AND e.start_at < :end
    GROUP BY ic.code, ic.description
    ORDER BY COUNT(*) DESC, ic.code ASC
    LIMIT 3
   ```
   (Mismo criterio de fecha que `topTreatments`: fecha de la atención.)
5. `topTreatments`: `LIMIT 5` → `LIMIT 3`; nada más cambia.
6. Conversión de resultados como en la 020 (`((Number) v).longValue()`, montos con escala 2 `HALF_UP`).

## Tests requeridos
**Unit:** ninguno.

**Integration** (`DashboardSummaryIT.summary_asAdminWithData_returnsExpectedFigures`, sin casos nuevos): en el setup,
tras abrir la atención, registrar un diagnóstico (`POST /encounters/{id}/diagnoses` con un `icd10Id` de la semilla,
p. ej. el primero de `GET /icd10?query=K02`). Aserciones nuevas (diferencias after − before, como las existentes):
- en `byDay`, la entrada de `today`: `completedEncounters` +1, `newPatients` +1, `collected` +30.00;
  `byDay` tiene exactamente 1 elemento (rango de un día);
- `topDiagnoses` contiene el código registrado con `occurrences` ≥ 1 y su tamaño es ≤ 3;
- `topTreatments` tiene tamaño ≤ 3.

## Resultado
- **Qué se hizo**:
  - Creados los DTOs `TopDiagnosisResponse` y `DailyStat`.
  - Añadidos al final de `DashboardSummaryResponse` los campos `List<TopDiagnosisResponse> topDiagnoses` y `List<DailyStat> byDay`.
  - Modificado `DashboardRepository`: ajustado `topTreatments` a `LIMIT 3`, y agregados los 5 métodos de consulta (`appointmentsByDay`, `completedEncountersByDay`, `newPatientsByDay`, `collectedByDay`, `topDiagnoses`).
  - Actualizado `DashboardService.summarize`: genera la lista diaria `byDay` para todos los días en el rango (`from` a `to` inclusive con valores por defecto en cero) y mapea `topDiagnoses`.
  - Actualizado `DashboardSummaryIT.summary_asAdminWithData_returnsExpectedFigures` con las nuevas aserciones para `byDay`, `topDiagnoses` y `topTreatments`.
- **Resultado de los tests**:
  - Surefire: Tests run: 103, Failures: 0, Errors: 0, Skipped: 0.
  - Failsafe: Tests run: 79, Failures: 0, Errors: 0, Skipped: 0 (incluyendo `DashboardSummaryIT`: 2 tests).
- **Problemas encontrados y supuestos tomados**:
  - En las consultas de agregación diaria de PostgreSQL por zona horaria parametrizada (`CAST((start_at AT TIME ZONE :zone) AS date)`), se utilizó `GROUP BY 1` para evitar discrepancias de resolución de parámetros en la cláusula `GROUP BY`.

## Revisión

Estado: APROBADO

Cumple los criterios 1–6: `byDay` con un día por fecha del rango (ceros incluidos), día en la zona de la clínica,
top 3 de tratamientos y diagnósticos, campos añadidos al final del record.
Correcciones mínimas de Claude:
1. `DashboardRepository.toLocalDate` usaba `java.sql.Date` con nombre calificado → `import java.sql.Date` (clean-code).
2. `DashboardSummaryIT`: la aserción «`topDiagnoses` contiene K02.1» dependía del orden de ejecución (otros IT
   registran diagnósticos hoy y pueden dejar K02.1 fuera del top 3). Ahora valida que no esté vacío, tamaño ≤ 3 y
   orden descendente por `occurrences`.
`./mvnw -q clean verify` (junto con la 025): surefire 103/0/0, failsafe 79/0/0.
