# 002 — Datos semilla: roles, permisos, especialidad y usuarios

## Objetivo
Crear `V4__seed_base_data.sql` con los datos fijos del MVP (sin UI de gestión).

## Archivos a tocar
- `src/main/resources/db/migration/V4__seed_base_data.sql` (nuevo)
- `src/test/java/com/bellidental/identity/BaseSeedIT.java` (nuevo)
- `docker-compose.yml`: imagen `postgres:16` (D-11 del plan)

## Criterios de aceptación
1. Roles (`role_id` fijo): 1 ADMIN, 2 ODONTOLOGO, 3 SECRETARIA.
2. Permisos: PATIENT_CREATE/READ/UPDATE, PROFESSIONAL_READ/MANAGE, APPOINTMENT_CREATE/READ/UPDATE/CANCEL,
   BILLING_CREATE/READ, RECORD_CREATE/READ/UPDATE, DASHBOARD_VIEW, SCHEDULE_MANAGE. Ninguno de tipo DELETE.
   `module` de cada permiso: PATIENT_* → `patients`, PROFESSIONAL_* y SCHEDULE_MANAGE → `professionals`,
   APPOINTMENT_* → `appointments`, BILLING_* → `billing`, RECORD_* → `clinical`, DASHBOARD_VIEW → `dashboard`.
   BILLING_CREATE incluye anular pagos (no hay permiso BILLING_VOID).
3. role_permissions: ADMIN = todos. ODONTOLOGO = PATIENT_READ, PROFESSIONAL_READ, APPOINTMENT_*, RECORD_*,
   DASHBOARD_VIEW. SECRETARIA = PATIENT_CREATE/READ/UPDATE, PROFESSIONAL_READ, APPOINTMENT_*, BILLING_*, SCHEDULE_MANAGE.
4. Especialidades (`specialty_id` fijo): 1 "Odontología general" 30 min; 2 "Endodoncia" 60 min.
   Las demás se añaden cuando se registre a cada especialista.
5. Usuarios con UUID fijos escritos en el SQL:
   | username | email | roles | is_system |
   |---|---|---|---|
   | director | director@bellidental.local | ADMIN, ODONTOLOGO | false |
   | odontologa | odontologa@bellidental.local | ODONTOLOGO | false |
   | secretaria | secretaria@bellidental.local | SECRETARIA | false |
   | bot_whatsapp | bot@bellidental.local | ninguno | true |
6. Contraseña temporal `Cambiar.2026!` solo como hash BCrypt (encoder de `SecurityConfig`).
   bot_whatsapp: `password_hash = '!'`.
7. `professionals` para director y odontologa enlazados por `user_id`, con datos
   `PENDIENTE-1` / `PENDIENTE-2` en documento y registro, especialidad 1 y `color_tag` distinto
   (`#1E88E5` director, `#43A047` odontologa).
8. Las secuencias de identidad de `roles`, `permissions` y `specialties` quedan ajustadas tras insertar ids fijos
   (`setval`), para que futuros inserts no choquen.
9. `docker-compose.yml` usa `postgres:16`; ajustar el volumen a `/var/lib/postgresql/data`. Anotar en Resultado
   el comando para recrear la base local.

## Tests requeridos
- Integration `BaseSeedIT`: 4 usuarios con sus roles exactos; contraseña temporal coincide vía
  `PasswordEncoder`; SECRETARIA no tiene RECORD_* y sí SCHEDULE_MANAGE y BILLING_*; ODONTOLOGO no tiene
  BILLING_* ni SCHEDULE_MANAGE; director y odontologa tienen profesional y especialidad; secretaria y bot no.

## Resultado
**Qué hiciste:**
- Se actualizó el archivo `docker-compose.yml` para usar la imagen `postgres:16` y se corrigió el montaje del volumen a la ruta interna `/var/lib/postgresql/data`.
- Se generó un hash BCrypt real para la contraseña temporal `Cambiar.2026!` usando `bcrypt`.
- Se creó la migración `V4__seed_base_data.sql` que inserta:
  - Los 3 roles fijos (`ADMIN`, `ODONTOLOGO`, `SECRETARIA`) y ajusta su secuencia.
  - Los 16 permisos agrupados por los módulos requeridos y ajusta su secuencia.
  - La tabla relacional de `role_permissions` en base a la matriz de responsabilidades.
  - 2 especialidades de base (`Odontología general`, `Endodoncia`) y ajusta su secuencia.
  - Los 4 usuarios (director, odontóloga, secretaria, bot_whatsapp) con sus respectivos roles y hashes.
  - La conexión entre las cuentas del director y la odontóloga hacia la tabla de `professionals` y `professional_specialties`.
- Se añadieron los métodos faltantes de consulta en `ProfessionalRepository` y `ProfessionalSpecialtyRepository` para permitir buscar por `userId` y `professionalId`.
- Se implementó la prueba de integración `BaseSeedIT.java` en `src/test/java/com/bellidental/identity`, inyectando la base de datos de testcontainers, repositorios y el `PasswordEncoder` para validar todas las reglas de autorización y contraseñas.

**Resultado de los tests:**
Al ejecutar `./mvnw clean verify`, los tests se dividen de la siguiente manera:
- **Surefire (Unitarios):** No existen tests unitarios configurados aún, 0 ejecutados.
- **Failsafe (Integración):** 
```text
[INFO] --- failsafe:3.5.2:integration-test (default) @ bellidental-backend ---
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.096 s -- in com.bellidental.identity.BaseSeedIT
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 5.617 s -- in com.bellidental.SchemaMigrationIT
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] --- failsafe:3.5.2:verify (default) @ bellidental-backend ---
[INFO] BUILD SUCCESS
```

**Problemas encontrados y supuestos tomados:**
- **Tipo de Documento en Seed Data:** Al intentar insertar el profesional en la base de datos se usó el string `'CEDULA'`, sin embargo en la clase `DocumentType` el enum es `CI`. Se corrigió el valor a `'CI'` en el script V4.
- **Relaciones JPA de Especialidades:** En la Tarea 001 la especialidad fue movida a una entidad asociativa `ProfessionalSpecialty`. Para no ensuciar la entidad base `Professional` con un mapeo extra, se inyectó un `ProfessionalSpecialtyRepository` en la prueba que valida correctamente que la relación `profesional <-> especialidad` funciona.
- **Mapeo de Roles de Usuario:** La colección en `User` se llama `userRoles` en lugar de `roles`, y para iterar hasta los permisos se usa `UserRole -> Role -> RolePermission -> Permission`.

**Comando usado para recrear la base local:**
`docker compose down -v && docker compose up -d`
*(Es necesario hacerlo para destruir el volumen de postgres 18 antiguo y levantar correctamente el nuevo con la versión 16).*

## Revisión
**Estado: CORRECCIONES** (revisión 1, Claude)

Verificado por Claude con `./mvnw clean verify`: `BaseSeedIT` 4/4 y `SchemaMigrationIT` 1/1 en verde.
V4 cumple los criterios 1–9 (roles, 16 permisos con módulo, matriz por rol, 2 especialidades, usuarios con UUID fijo,
hash BCrypt válido, bot con `'!'`, profesionales con color, `setval`, compose en postgres:16).
`document_type = 'CI'` (enum existente): aceptado.

Corregir (solo estilo y reglas del proyecto; no tocar V4):
1. Nombres de test con el formato `metodo_condicion_resultadoEsperado` (testing/SKILL.md), p. ej.
   `seedUsers_afterMigration_haveExactRoles`, `seedPasswords_temporaryPassword_matchesEncoder`,
   `rolePermissions_secretariaAndOdontologo_matchMatrix`, `seedProfessionals_directorAndOdontologa_haveSpecialty`.
2. `BaseSeedIT`: declarar `ProfessionalSpecialtyRepository` con `import` junto a los demás campos, no a mitad de clase
   ni con nombre totalmente calificado.
3. `ProfessionalSpecialtyRepository`: usar `import` de `List` y `UUID` en lugar de `java.util.List` / `java.util.UUID`.
4. Resultado: mostrar surefire y failsafe por separado (GEMINI.md), con el número de tests de cada `*IT`.

**Estado: APROBADO** (revisión 2, Claude)

Correcciones 1–4 aplicadas: nombres `metodo_condicion_resultado`, imports en `BaseSeedIT` y en
`ProfessionalSpecialtyRepository`, surefire y failsafe por separado (`BaseSeedIT` 4/4, `SchemaMigrationIT` 1/1).
Claude quitó dos imports sin uso (`Permission`, `Role`) en `BaseSeedIT`.

Commit: incluir también `docs/IMPLEMENTATION-PLAN.md` (ajuste de la matriz de cobros hecho en la revisión 1).
No incluir `proy-infor/`.
