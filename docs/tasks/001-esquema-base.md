# 001 — Esquema base: identity, staff y patients

## Objetivo
Reemplazar el V1 antiguo por tres migraciones alineadas con `docs/schema.dbml`
(secciones 1, 2 y 3) y ajustar las entidades para que la app arranque con `ddl-auto=validate`.
La base local es solo de desarrollo: se recrea desde cero.

## Archivos a tocar
- Borrar contenido antiguo de `db/migration/V1__initial_schema.sql`; reemplazar por:
  - `V1__identity.sql` (users, roles, permissions, role_permissions, user_roles)
  - `V2__staff.sql` (professionals, specialties, professional_specialties)
  - `V3__patients.sql` (patients)
- Entidades en `persistence/entity` y repositorios en `persistence/repository` de esas tablas.
- `patients` y `professionals`: DTOs y servicios solo lo necesario para compilar
  (quitar campos eliminados, renombrar `gender`→`sex`, `phone`→`phoneE164`; sin funcionalidad nueva).
- `src/test/java/com/bellidental/SchemaMigrationIT.java` + dependencias Testcontainers.

## Criterios de aceptación
1. Columnas, tipos, nulos, únicos e índices idénticos a `schema.dbml` secciones 1–3, con CHECK de `sex`.
2. Sin datos semilla en estas migraciones (van en la tarea 002).
3. `CREATE EXTENSION IF NOT EXISTS pgcrypto` en V1.
4. `professionals.specialty` ya no existe; la especialidad se modela con `professional_specialties`.
5. La app arranca contra una base vacía; Hibernate valida sin errores.
6. No se borran módulos ni controladores existentes.
7. Anotar en Resultado el comando usado para recrear la base local.

## Tests requeridos
- Integration `SchemaMigrationIT` (Testcontainers PostgreSQL 16): el contexto de Spring carga,
  Flyway aplica V1–V3 y Hibernate valida.

## Resultado
**Qué hiciste:**
- Se eliminó el archivo antiguo `V1__initial_schema.sql`.
- Se crearon las migraciones `V1__identity.sql`, `V2__staff.sql` y `V3__patients.sql` alineadas con `docs/schema.dbml`.
- Se ajustaron las entidades:
  - `User`, `Role`, `Permission`, `RolePermission`, `UserRole`: se cambió UUID a Long donde correspondía, se adaptó isSystem, lastLoginAt.
  - `Professional`: se quitó specialty y email, se añadió googleCalendarId, phoneE164. Se creó la entidad `Specialty` y `ProfessionalSpecialty`.
  - `Patient`: se renombró gender a sex (con su respectiva mapeo `bpchar`), phone a phoneE164, se quitó address y user_id (para ajustarse al DBML), y se añadió adminNotes.
- Se actualizaron los repositorios, los DTOs y los servicios eliminando los campos incompatibles o inexistentes según el nuevo esquema y ajustando la compilación.
- Se añadió Testcontainers al proyecto y se configuró la prueba `SchemaMigrationIT`.

**Resultado de los tests:**
Al ejecutar `./mvnw clean verify`, los tests se dividen correctamente:
- **Surefire (Unitarios):** No hay tests unitarios configurados aún tras la eliminación de la prueba duplicada de contexto, por lo que surefire no ejecuta tests.
- **Failsafe (Integración):**
```text
[INFO] --- failsafe:3.5.2:integration-test (default) @ bellidental-backend ---
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 47.76 s - in com.bellidental.SchemaMigrationIT
[INFO]
[INFO] --- failsafe:3.5.2:verify (default) @ bellidental-backend ---
[INFO] BUILD SUCCESS
```
La validación de Hibernate con `ddl-auto=validate` comprobó satisfactoriamente que las entidades concuerdan con el esquema de base de datos Flyway mediante Testcontainers en la fase de integración.

**Problemas encontrados y supuestos tomados:**
- **TestcontainersConfiguration:** Se migró la configuración estática de `Testcontainers` a una clase de configuración `@TestConfiguration` utilizando la anotación moderna `@ServiceConnection` para simplificar la inyección de la base de datos PostgreSQL en las pruebas de integración.
- **Versión Docker API / Testcontainers:** Se mantuvo explícitamente `<testcontainers.version>1.21.4</testcontainers.version>` en el `pom.xml`, dado que la versión que provee el BOM de Spring Boot 3.4.1 entraba en conflicto con la versión antigua del API del cliente Docker local (`client version 1.32 is too old`). Esto garantiza la ejecución correcta en este entorno particular.
- **Validación de Hibernate (`sex bpchar` vs `varchar(1)`):** El esquema PostgreSQL creaba la columna `sex` como tipo interno `bpchar`, pero Hibernate la esperaba como `varchar(1)` con `@Column(length=1)`. Se resolvió usando explícitamente `columnDefinition = "bpchar"`.
- **Inconsistencia de user_id en Pacientes:** La entidad `Patient` poseía la relación con `User`, pero `schema.dbml` no listaba `user_id` en la tabla `patients`. Se eliminó esta relación por completo en la capa JPA, DTOs y Servicios de acuerdo a la instrucción estricta de no añadir columnas no presentes en el esquema propuesto.

**Comando usado para recrear la base local:**
`docker compose down -v && docker compose up -d`

## Revisión
**Estado: APROBADO** (revisión 2, Claude)

Verificado:
- Correcciones 1–4 aplicadas: failsafe en `pom.xml`, `support/TestcontainersConfiguration` con
  `@ServiceConnection` (`postgres:16-alpine`), test duplicado borrado, `SpecialtyRepository` y
  `ProfessionalSpecialtyRepository` creados.
- Corrección 5: se mantiene `testcontainers.version` 1.21.4 por el error de API de Docker; aceptado.
- `./mvnw clean verify` corrido por Claude: surefire 0 tests; failsafe `SchemaMigrationIT` 1/1 OK.
- V1–V3 coinciden con `schema.dbml` §1–3.

Nota menor (no bloquea): en Resultado, la línea "se configuraron `SchemaMigrationIT` y
`BellidentalBackendApplicationTests`" quedó desactualizada; el segundo ya no existe.

Commit (en este orden):
1. `docs: flujo de trabajo Claude/Gemini, alcance MVP, contexto y plan` con `CLAUDE.md`, `GEMINI.md`,
   `.gemini/`, `.agents/`, `docs/MVP-SCOPE.md`, `docs/schema.dbml`, `docs/CONTEXT.md`,
   `docs/IMPLEMENTATION-PLAN.md`, `docs/tasks/002-datos-semilla.md` y `docs/tasks/003-login-email.md`.
   No incluir `proy-infor/`, `prompt.txt`, `new-database.txt`, `tasks.txt` ni `structure.txt`.
2. `feat(schema): migraciones identity, staff y patients (tarea 001)` con el código y este archivo.
