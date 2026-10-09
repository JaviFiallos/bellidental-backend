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
_(Lo llena Gemini)_
