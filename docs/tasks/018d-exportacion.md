# 018d — Exportación abierta de datos (ZIP de CSV)

## Objetivo
RNF04, plan §1 y §4.12: el director puede descargar todos los datos del sistema en un formato abierto, sin
PostgreSQL. `GET /api/v1/admin/export` (solo `ADMIN`) devuelve un ZIP con un CSV por tabla (UTF-8, con cabecera),
incluidas las tablas de auditoría `*_aud`. El dump SQL ya lo cubren los respaldos (018c). El frontend añade el botón
de descarga en su tarea propia (frontend 029).

**Estructura**: módulo nuevo `admin` (paquete `com.bellidental.admin`), sin `Directory` (nadie lo usa) ni
dependencias de otros módulos. Lee el catálogo de `information_schema` y vuelca cada tabla con
`COPY (...) TO STDOUT WITH (FORMAT csv, HEADER)` mediante `CopyManager` del driver de PostgreSQL.

## Archivos a tocar
```
src/main/java/com/bellidental/admin/
  api/AdminExportController.java          (nuevo) GET /api/v1/admin/export
  application/DataExportService.java      (nuevo) escribe el ZIP sobre un OutputStream
  domain/CsvTableExport.java              (nuevo) puro: orden de tablas, nombre de entrada, SELECT de columnas
src/test/java/com/bellidental/admin/
  AdminExportIT.java                      (nuevo)
docs/OPERATIONS.md                        sección "Exportar"
docs/IMPLEMENTATION-PLAN.md               §4.12: endpoint con su forma final
```

## Criterios de aceptación
1. `@PreAuthorize("hasRole('ADMIN')")`; otros perfiles → 403; sin token → 401 (como el resto de la API).
2. Controlador **síncrono**: `void export(HttpServletResponse response)`; pone las cabeceras y pasa
   `response.getOutputStream()` al servicio. **No** usar `StreamingResponseBody` ni `ResponseEntity<Resource>`
   (el primero hace un *async dispatch* que el filtro JWT no autentica; el segundo carga todo en memoria).
   Cabeceras: `Content-Type: application/zip`,
   `Content-Disposition: attachment; filename="bellidental-export-<yyyyMMdd-HHmm>.zip"` (hora de `ClinicClock`) y
   `Access-Control-Expose-Headers: Content-Disposition`.
3. Streaming real: `ZipOutputStream` sobre el `OutputStream` de la respuesta; cada `COPY` escribe directo en la entrada
   abierta del ZIP (envolver el stream para que `copyOut` no cierre el ZIP). Nada de `ByteArrayOutputStream` por tabla.
4. `DataExportService.export(OutputStream)` con `@Transactional(readOnly = true, isolation = REPEATABLE_READ)`.
   La conexión del `COPY` es **la de la transacción**: `DataSourceUtils.getConnection(dataSource)` y
   `connection.unwrap(PGConnection.class).getCopyAPI()` (con Hikari, `unwrap` llega al `PgConnection`). Así todas
   las tablas salen de la misma foto.
5. Tablas: `information_schema.tables` con `table_schema = 'public'` y `table_type = 'BASE TABLE'`, excepto
   `flyway_schema_history`. Una entrada `<tabla>.csv` por tabla, en orden alfabético.
6. Columnas: de `information_schema.columns` en `ordinal_position`. Cada tabla se vuelca como
   `COPY (SELECT "c1", "c2", ... FROM "tabla") TO STDOUT WITH (FORMAT csv, HEADER)` con identificadores entre comillas
   dobles; una columna llamada `password_hash` se sustituye por `NULL AS "password_hash"` (la cabecera se conserva y
   el valor sale vacío). La construcción del SELECT y el orden viven en `CsvTableExport` (sin Spring).
7. `README.txt` (primera entrada): fecha y hora de la exportación (zona de la clínica), versión de Flyway
   (`SELECT max(version) FROM flyway_schema_history WHERE success`), número de tablas y la línea
   "CSV UTF-8 con cabecera; ids uuid; fechas ISO 8601 con zona; password_hash vacío".
8. Log INFO al terminar: `userId` del `CurrentUser`, número de tablas y duración. Nunca datos de las filas.
9. Si falla a mitad (la respuesta ya empezó), se registra ERROR y se deja que la excepción cierre la conexión; no se
   intenta escribir un `ApiResponse` sobre un ZIP a medias.
10. Sin migraciones, permisos ni cambios en `security/`.
11. `docs/OPERATIONS.md` §Exportar: qué contiene, que se descarga desde Gestión → "Exportar datos" (frontend 029), y
    el `curl` alternativo:
    `TOKEN=$(curl -s https://app.<dominio>/api/v1/auth/login -H 'Content-Type: application/json'
    -d '{"email":"…","password":"…"}' | jq -r .data.accessToken)` y
    `curl -fOJ -H "Authorization: Bearer $TOKEN" https://app.<dominio>/api/v1/admin/export`.
    Comprobar en el Caddyfile la ruta real de la API antes de escribir el host.
12. `./mvnw -q clean verify` pasa.

## Tests requeridos
- **Unit:** ninguno.
- **Integration:** `AdminExportIT` (2 casos, `TestcontainersConfiguration`, login por API como los demás IT):
  1. `export_asAdmin_returnsZipWithAllTablesAndNoPasswordHashes`: `director@bellidental.local` → 200,
     `Content-Type` `application/zip`, `Content-Disposition` con `bellidental-export-`. Leer el cuerpo con
     `ZipInputStream`: primera entrada `README.txt`; contiene `patients.csv`, `users.csv` y al menos un `*_aud.csv`;
     no contiene `flyway_schema_history.csv`; la cabecera de `users.csv` incluye `password_hash` y ninguna fila
     contiene `$2` (prefijo de bcrypt).
  2. `export_asSecretaria_returnsForbidden`: `secretaria@bellidental.local` → 403.

## Resultado

**Archivos creados**
- `src/main/java/com/bellidental/admin/api/AdminExportController.java`: `GET /api/v1/admin/export`, `@PreAuthorize("hasRole('ADMIN')")`,
  síncrono (`void export(HttpServletResponse)`), cabeceras `application/zip`, `Content-Disposition` con
  `bellidental-export-<yyyyMMdd-HHmm>.zip` (hora de la clínica) y `Access-Control-Expose-Headers`.
- `src/main/java/com/bellidental/admin/application/DataExportService.java`: `@Transactional(readOnly = true, isolation = REPEATABLE_READ)`;
  `DataSourceUtils.getConnection` + `unwrap(PGConnection.class).getCopyAPI().copyOut(...)`; `ZipOutputStream` sobre la respuesta;
  envoltorio que no cierra el ZIP entre tablas; README.txt primero; log INFO (userId, nº tablas, duración); en fallo a mitad, log ERROR y re-lanza.
- `src/main/java/com/bellidental/admin/domain/CsvTableExport.java`: puro. Filtra `flyway_schema_history`, ordena, nombre `<tabla>.csv`,
  SELECT con identificadores entre comillas y `NULL AS "password_hash"`, y nombre del archivo.
- `src/test/java/com/bellidental/admin/AdminExportIT.java`: 2 casos (admin → ZIP completo y sin hashes; secretaria → 403).

**Archivos modificados**
- `docs/OPERATIONS.md`: sección "Exportar" (contenido, descarga desde Gestión, `curl` alternativo).
  El host es `app.<dominio>` porque `deploy/Caddyfile` envía `/api/*` a `backend:8080` dentro del host `app.{$DOMAIN}`.
- `docs/IMPLEMENTATION-PLAN.md`: §4.12, forma final del endpoint (`/api/v1/admin/export`).
- `pom.xml`: **cambio fuera de `## Archivos a tocar`** (ver problemas): `postgresql` pasa de `runtime` a compile (se quitó `<scope>runtime</scope>`).

**Verificaciones**
- `./mvnw -q verify -Dit.test=AdminExportIT ...` (durante el desarrollo): `AdminExportIT` 2 de 2 OK.
- `./mvnw -q clean verify` (una vez): exit 0.
  - surefire: 103 tests, 0 fallos, 0 errores, 0 skipped.
  - failsafe: 103 tests (29 clases IT), 0 fallos, 0 errores, 0 skipped; `AdminExportIT` 2 de 2.

**Problemas y supuestos**
- El criterio 4 exige `unwrap(PGConnection.class)`, que no compila con el driver en `runtime`. Para cumplirlo se quitó
  `<scope>runtime</scope>` de `org.postgresql:postgresql` en `pom.xml`. Es un cambio de dependencias fuera de la lista de
  archivos; el coordinador debe confirmarlo. La alternativa sería no usar `PGConnection`, lo que incumple el criterio 4.
- La transacción REPEATABLE READ y el `COPY` comparten la misma conexión (`DataSourceUtils`); `JdbcTemplate` también la usa, así que
  el README, el catálogo y los COPY salen de la misma foto.
- Tabla sin columnas: no se contempla (todas las tablas del esquema tienen columnas).
- Sin tests unitarios (la tarea no los pide); el dominio se cubre vía el IT.
- Si falla a mitad, la respuesta ya está comprometida: `DataAccessResourceFailureException` (SQL) o la excepción original
  (I/O o runtime) se re-lanza, y el manejador global no puede escribir un `ApiResponse` sobre el ZIP a medias.

## Revisión
Estado: APROBADO

- Controlador síncrono (`void export(HttpServletResponse)`) con las tres cabeceras y `@PreAuthorize("hasRole('ADMIN')")`;
  llama a `DataExportService.export` por el bean inyectado (proxy de Spring), así que `@Transactional(readOnly,
  REPEATABLE_READ)` aplica. `DataSourceUtils.getConnection` + `unwrap(PGConnection)`: el catálogo, el README y los
  `COPY` salen de la misma conexión y foto.
- Streaming real: `ZipOutputStream` directo sobre la respuesta, `copyOut` a un `FilterOutputStream` que no cierra,
  `finish()` sin `close()`; sin buffers por tabla.
- `CsvTableExport` puro: filtra `flyway_schema_history`, orden alfabético, identificadores entre comillas con escape de
  `"`, `NULL AS "password_hash"`. Consulta solo `BASE TABLE` de `public`. README primero con la línea exacta.
- Log INFO con userId, número de tablas y duración; ERROR en fallo a mitad y re-lanza; sin datos de filas.
- `pom.xml`: solo se quita `<scope>runtime</scope>` de `postgresql` (aceptado por el coordinador). Sin cambios en
  `security/` ni migraciones. OPERATIONS e IMPLEMENTATION-PLAN correctos. IT con 2 casos y lectura real del ZIP;
  totales reportados: surefire 103/0, failsafe 103/0.
