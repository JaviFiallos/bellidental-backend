# 029 — Lista de pacientes: últimos atendidos y últimos registrados, con saldo (G-16 del frontend)

## Objetivo
El frontend añade la pantalla `/pacientes` (frontend 005c): una tabla **informativa y paginada** con dos vistas,
«Últimos atendidos» y «Últimos registrados», filtrable por período, por texto y por «solo con saldo pendiente». Hoy
`GET /api/v1/patients` solo busca por texto y no trae ni la última atención ni el saldo. Calcular el saldo por
paciente desde el frontend sería una petición por fila, así que hace falta **un endpoint con todo ya calculado en una
sola consulta**. Solo lectura: no cambia ningún dato.

## Contrato
```
GET /api/v1/patients/list
  ?view=ATTENDED|CREATED        obligatorio
  &from=YYYY-MM-DD&to=YYYY-MM-DD opcionales, juntos (fechas de la clínica, America/Guayaquil, ambos inclusive)
  &withBalance=true              opcional (por defecto false): solo pacientes con saldo > 0
  &query=texto                   opcional: nombre, apellido o cédula (misma búsqueda que GET /patients)
  &page=0&size=20                size máx. 50
→ ApiResponse<Page<PatientListItem>>   (mismo Page que GET /patients y GET /audit)

record PatientListItem(
    UUID id, String firstName, String lastName, String fullName,
    DocumentType documentType, String documentNumber, Integer age,
    OffsetDateTime lastEncounterAt,   // null si nunca fue atendido
    OffsetDateTime createdAt,
    BigDecimal pendingBalance          // null si el usuario no tiene BILLING_READ
)
```

## Archivos a tocar
```
src/main/java/com/bellidental/patients/
  PatientController.java                       (modif.) GET /list
  PatientListService.java                      (nuevo) validaciones, permiso del saldo, mapeo
  PatientListItem.java                         (nuevo) record de la respuesta
  PatientListView.java                         (nuevo) enum ATTENDED, CREATED
src/main/java/com/bellidental/persistence/repository/
  PatientListQueryRepository.java              (nuevo) consulta nativa + count (patrón de AuditQueryRepository)
src/main/resources/db/migration/V21__patient_list_indexes.sql   (nuevo) índices
src/test/java/com/bellidental/patients/PatientListIT.java       (nuevo)
docs/IMPLEMENTATION-PLAN.md                    tabla §3 (fila 029), endpoint en §4 de pacientes, D-31
```
`GET /patients/{id}` ya está en la ruta `/{id}` con `UUID`: comprobar que `/list` no choca (mapeo literal primero o
`/{id:[0-9a-f-]{36}}`).

## Criterios de aceptación
1. **Permiso:** `PATIENT_READ` (los tres perfiles). `pendingBalance` se calcula y se devuelve **solo** si el usuario
   tiene `BILLING_READ`. Sin él va `null`, y `withBalance=true` → 403 «No tienes permiso para ver saldos.».
2. **Solo pacientes activos.** Saldo = Σ(`performed_treatments.price × quantity` con `voided_at IS NULL`, de atenciones
   no anuladas) − Σ(`payments.amount` con `voided_at IS NULL`). Debe dar **lo mismo** que `AccountService` para el mismo
   paciente (`AccountBalance.balance`, escala 2). Si hay que excluir algo más que `AccountService` excluye (sesiones
   anuladas, etc.), replicarlo y anotarlo en Resultado.
3. **`view=ATTENDED`:** solo pacientes con al menos una atención no anulada (`encounters.voided_at IS NULL`);
   `lastEncounterAt = max(encounters.start_at)`; orden `lastEncounterAt DESC, lastName, firstName`. Con `from/to`, el
   paciente entra si **su última atención** cae en el rango (en la zona de la clínica).
4. **`view=CREATED`:** todos los activos; orden `createdAt DESC`; con `from/to`, `createdAt` en el rango.
   `lastEncounterAt` se devuelve igual (puede ser `null`).
5. **Validaciones (400 con mensaje en español):**
   - `view` ausente o inválido: «Indica la vista: ATTENDED o CREATED.».
   - Solo una fecha: «Indica las dos fechas del período, o ninguna.».
   - `from > to`: «La fecha inicial no puede ser posterior a la final.».
   - Rango > 366 días: «El período no puede superar 366 días.». Es el mismo límite que el selector de período del
     frontend.
   - `size` > 50 → se recorta a 50 (no es error).
6. **Una sola consulta** para la página (más el `count`), sin N+1: agregados por paciente con `LEFT JOIN` a
   subconsultas agrupadas (o CTE), y `LIMIT/OFFSET`. `withBalance` filtra en SQL (`HAVING`/`WHERE saldo > 0`), no en
   memoria, para que la paginación sea correcta.
7. **V21:** índices para la consulta, si no existen ya. Por ejemplo:
   - `encounters(patient_id, start_at DESC) WHERE voided_at IS NULL`;
   - `payments(patient_id) WHERE voided_at IS NULL`;
   - `patients(created_at DESC)`.
   Solo índices, sin cambios de datos.
8. La búsqueda por `query` reutiliza el criterio de `GET /patients`: sin distinguir mayúsculas ni tildes, si así
   funciona hoy. Sin duplicar el SQL si se puede extraer.

## Tests requeridos
**Unit:** ninguno.

**Integration** (`PatientListIT`, 5 casos):
1. `ATTENDED`: dos pacientes atendidos en días distintos y uno sin atención → salen los dos atendidos, el más reciente
   primero, con `lastEncounterAt`. El que no tiene atención no sale.
2. `CREATED` con `from/to` → solo los registrados en el rango, el más nuevo primero.
3. Saldo: paciente con un tratamiento de 50.00 y un abono de 20.00, más un abono anulado de 10.00 →
   `pendingBalance = 30.00`. Es igual al `balance` de `GET /patients/{id}/account`. `withBalance=true` excluye a uno
   con saldo 0.
4. Usuario sin `BILLING_READ` (rol de prueba solo con `PATIENT_READ`, o el que aplique) → `pendingBalance` `null`;
   con `withBalance=true` → 403.
5. Rango de 400 días → 400 con el mensaje.

## Resultado
- **Qué se hizo**:
  - Se creó la migración [V21__patient_list_indexes.sql](file:///home/javif/Documentos/UTA/Titulacion/bellidental-app/bellidental-backend/src/main/resources/db/migration/V21__patient_list_indexes.sql) con índices para optimizar la consulta (`encounters(appointment_id, start_at DESC) WHERE voided_at IS NULL`, `payments(patient_id) WHERE voided_at IS NULL`, `patients(created_at DESC)` y `performed_treatments(encounter_id) WHERE voided_at IS NULL`).
  - Se crearon los tipos del contrato en `com.bellidental.patients`: enum [PatientListView](file:///home/javif/Documentos/UTA/Titulacion/bellidental-app/bellidental-backend/src/main/java/com/bellidental/patients/PatientListView.java) y record [PatientListItem](file:///home/javif/Documentos/UTA/Titulacion/bellidental-app/bellidental-backend/src/main/java/com/bellidental/patients/PatientListItem.java).
  - Se implementó [PatientListQueryRepository](file:///home/javif/Documentos/UTA/Titulacion/bellidental-app/bellidental-backend/src/main/java/com/bellidental/persistence/repository/PatientListQueryRepository.java) en `com.bellidental.persistence.repository` con consulta nativa paginada y `count` sin N+1, agregando atenciones no anuladas (`lastEncounterAt = max(encounters.start_at)`), cargos de atenciones no anuladas (`SUM(price * quantity)` con `voided_at IS NULL`) y abonos no anulados (`SUM(amount)` con `voided_at IS NULL`), filtrando `withBalance` en SQL.
  - Se refactorizó [PatientListQueryRepository](file:///home/javif/Documentos/UTA/Titulacion/bellidental-app/bellidental-backend/src/main/java/com/bellidental/persistence/repository/PatientListQueryRepository.java) para eliminar duplicación de SQL entre `findPatientList` y `countPatientList` extrayendo los métodos privados `appendEncounterJoin`, `appendBalanceJoins`, `appendFilters` y la constante `BALANCE_EXPRESSION`.
  - Se alineó la búsqueda por `query` con el criterio de `PatientService.search`: si es numérico busca por prefijo en `document_number` o `phone_e164` (reutilizando `toPhonePrefix`); si es texto busca por `ILIKE` en `first_name`, `last_name` o nombre completo.
  - Se creó [ForbiddenException](file:///home/javif/Documentos/UTA/Titulacion/bellidental-app/bellidental-backend/src/main/java/com/bellidental/shared/exception/ForbiddenException.java) en `com.bellidental.shared.exception` y se añadió su manejador en [GlobalExceptionHandler](file:///home/javif/Documentos/UTA/Titulacion/bellidental-app/bellidental-backend/src/main/java/com/bellidental/shared/exception/GlobalExceptionHandler.java) retornando HTTP 403 con su mensaje.
  - Se implementó [PatientListService](file:///home/javif/Documentos/UTA/Titulacion/bellidental-app/bellidental-backend/src/main/java/com/bellidental/patients/PatientListService.java) con validaciones (`view` obligatorio, fechas en par, `from <= to`, rango <= 366 días, `size` máx. 50), chequeo de permisos (`BILLING_READ` para `pendingBalance` y `ForbiddenException` para `withBalance=true`), cálculo de `age` con zona horaria clínica, y mapeo a `Page<PatientListItem>`.
  - Se actualizó [PatientController](file:///home/javif/Documentos/UTA/Titulacion/bellidental-app/bellidental-backend/src/main/java/com/bellidental/patients/PatientController.java) con el endpoint `GET /api/v1/patients/list` protegido por `PATIENT_READ`, manteniendo `@GetMapping("/{id}")` sin regex (Spring prioriza el literal `/list` sin conflictos).
  - Se actualizó la sección §4.1 de [docs/IMPLEMENTATION-PLAN.md](file:///home/javif/Documentos/UTA/Titulacion/bellidental-app/bellidental-backend/docs/IMPLEMENTATION-PLAN.md) con el nuevo endpoint `GET /patients/list`.
  - Se crearon los 5 tests de integración requeridos en [PatientListIT](file:///home/javif/Documentos/UTA/Titulacion/bellidental-app/bellidental-backend/src/test/java/com/bellidental/patients/PatientListIT.java).
- **Resultado de los tests**:
  - Surefire: 103 tests run, 0 failures, 0 errors, 0 skipped (24 test classes)
  - Failsafe: 94 tests run, 0 failures, 0 errors, 0 skipped (28 test classes)
  - Todos los tests pasaron exitosamente en `./mvnw -q clean verify`.
- **Problemas encontrados y supuestos tomados**:
  - La tabla `encounters` se relaciona con `patients` mediante `appointments` (`encounters.appointment_id = appointments.appointment_id`), por lo que los agregados y filtros de atenciones se realizaron cruzando dicha clave foránea manteniendo la condición de exclusión `voided_at IS NULL`.
  - Para el test del usuario sin `BILLING_READ`, se configuró un rol de prueba `SOLO_LECTOR_PACIENTES` con únicamente permiso `PATIENT_READ`, verificando que `pendingBalance` sea `null` y que `withBalance=true` retorne 403 con «No tienes permiso para ver saldos.».

## Revisión
### Primera revisión
Estado: CORRECCIONES

Correcto: contrato, validaciones, permiso del saldo, filtros de `view`/período en SQL, paginación con `count`, V21 y
doc del plan (§3, §4.1, D-31). El saldo coincide con `AccountService` (tratamientos no anulados de atenciones no
anuladas − pagos no anulados del paciente, incluidos los ligados a una atención anulada). El rol de prueba por SQL en
`PatientListIT` se acepta: ningún rol de la semilla tiene `PATIENT_READ` sin `BILLING_READ` (D-8d).

1. **SQL duplicado en `PatientListQueryRepository`**: la subconsulta de última atención aparece 3 veces, la de
   cargos/abonos 2 veces, y los `WHERE` (período, saldo, texto) están copiados entre `findPatientList` y
   `countPatientList`. Extraer métodos privados que construyan una sola vez:
   - `appendEncounterJoin(sql, isAttendedView)` → `JOIN` o `LEFT JOIN` (solo cambia la palabra) de la misma subconsulta;
   - `appendBalanceJoins(sql)` → los dos `LEFT JOIN` de cargos y abonos, más una constante `BALANCE_EXPRESSION`
     (`COALESCE(ch.total_charges, 0) - COALESCE(pm.total_payments, 0)`) usada en el `SELECT` y en el filtro;
   - `appendFilters(sql, isAttendedView, hasRange, withBalance, hasQuery)` → el `WHERE` completo.
   `findPatientList` y `countPatientList` solo difieren en el `SELECT`, el `ORDER BY` y el `LIMIT/OFFSET`.
   Mismo SQL resultante; sin cambios de comportamiento.
2. **`@ExceptionHandler(AccessDeniedException)` dentro de `PatientController`**: quitarlo (cambia el 403 de todo el
   controlador y compara con el texto en inglés «Access Denied»). En su lugar, crear
   `shared/exception/ForbiddenException` (igual que `BusinessException`) y manejarla en `GlobalExceptionHandler` con
   403 y su `message`. `PatientListService` lanza `new ForbiddenException("No tienes permiso para ver saldos.")`.
   El handler existente de `AccessDeniedException` no se toca.
3. **Criterio 8 (búsqueda igual que `GET /patients`)**: hoy busca la cédula con `ILIKE %texto%` y no busca por
   teléfono. Replicar el criterio de `PatientService.search`: si el texto es numérico → `document_number` por
   **prefijo** u `phone_e164` por prefijo (mismo `queryPrefix` que arma `PatientService`); si no → nombre/apellido
   `ILIKE %texto%` (se puede conservar el `first_name || ' ' || last_name`). Si la decisión numérico/texto ya está en
   un método de `PatientService`, reutilizarlo en vez de copiarlo.
4. Revertir el cambio de `@GetMapping("/{id}")` a la regex: Spring ya prioriza el literal `/list` sobre `/{id}`. Si
   al revertirlo algún test falla, dejar la regex y anotarlo en Resultado.

Tests: no añadir casos; los 5 de `PatientListIT` deben seguir pasando. Actualizar `## Resultado` con los totales reales.

### Segunda revisión
Estado: APROBADO

Las 4 correcciones están aplicadas: SQL sin duplicar (`appendEncounterJoin`, `appendBalanceJoins`, `appendFilters`,
`BALANCE_EXPRESSION`), `ForbiddenException` manejada en `GlobalExceptionHandler`, búsqueda igual que `GET /patients`
(prefijo de cédula o teléfono con `PatientService.toPhonePrefix`, nombre con `ILIKE`) y `/{id}` sin regex.
Corrección menor de Claude: se quitó `@ResponseStatus` de `ForbiddenException` (redundante con el handler global,
igual que `BusinessException`).

`./mvnw -q clean verify` (Claude): Surefire 103/0/0/0 · Failsafe 94/0/0/0.

