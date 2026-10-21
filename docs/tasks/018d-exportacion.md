# 018d — Exportación abierta de datos (ZIP de CSV)

## Objetivo
RNF04, plan §1 y §4.12: el director puede descargar todos los datos del sistema en un formato abierto.
`GET /api/v1/admin/export` (solo `ADMIN`) devuelve un ZIP en streaming con un CSV por tabla (UTF-8, con
cabecera), incluidas las tablas de auditoría `*_aud`. Se deja una línea de log INFO con el usuario y la hora.
El dump SQL ya lo cubren los respaldos (018c); este ZIP es para leer los datos sin PostgreSQL.

**Estructura**: módulo nuevo `admin` (paquete `com.bellidental.admin`). Lee el
catálogo de tablas de `information_schema` del esquema `public` (excepto `flyway_schema_history`) y vuelca cada
una con `COPY ... TO STDOUT WITH (FORMAT csv, HEADER)` mediante `CopyManager` del driver de PostgreSQL. No
depende de los `Directory` de otros módulos.

## Archivos a tocar
```
admin/
  api/AdminExportController.java          (nuevo) GET /api/v1/admin/export
  application/DataExportService.java      (nuevo) escribe el ZIP sobre el OutputStream (StreamingResponseBody)
  domain/CsvTableExport.java              (nuevo) nombre de entrada del ZIP y orden de las tablas (puro)
```

## Criterios de aceptación
1. Solo `ADMIN`; otros perfiles → 403.
2. Respuesta `application/zip`, `Content-Disposition: attachment; filename="bellidental-export-<YYYYMMDD-HHmm>.zip"`
   (hora de `America/Guayaquil`).
3. Streaming: no se carga el ZIP completo en memoria (el backend tiene 1 GB).
4. Una entrada `<tabla>.csv` por tabla, en orden alfabético, más `README.txt` con la fecha, la versión de Flyway
   y una línea: "CSV UTF-8 con cabecera; ids uuid; fechas ISO 8601 con zona".
5. Las columnas `password_hash` se exportan vacías.
6. Transacción de solo lectura con aislamiento `REPEATABLE READ` para que todas las tablas sean coherentes.

## Tests requeridos
- Integration: `AdminExportIT` (2 casos)
  1. `ADMIN` descarga → 200, ZIP válido, contiene `patients.csv` con cabecera y `users.csv` sin hashes.
  2. `SECRETARIA` → 403.
- Unit: ninguno (el orden y los nombres se validan en el IT).

## Resultado

## Revisión
