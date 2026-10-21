# Plan de implementación — Bellidental MVP

Cómo se construye cada módulo de `MVP-SCOPE.md`. Cada §4.x es la base de una tarea `docs/tasks/NNN-*.md`:
la tarea copia de aquí objetivo, reglas y tests, y añade los archivos concretos.
Contexto de negocio: `CONTEXT.md`. Datos: `schema.dbml`.

Marcas: **[D-n]** = decisión pendiente (ver §6). Nada marcado así se implementa hasta aprobarse.

---

## 1. Arquitectura y despliegue (RNF04, RNF05)

**Topología (D-17: VPS en la nube; reemplaza a D-4, el PC de recepción):**
```
VPS (2 vCPU, 4 GB RAM) · Docker Compose · restart: unless-stopped
 ├─ caddy       único servicio con puertos públicos (80/443); HTTPS automático (Let's Encrypt)
 │   ├─ app.<dominio>           → frontend
 │   ├─ app.<dominio>/api/v1/   → backend   (bloquea /api/v1/bot/** desde internet)
 │   └─ bot.<dominio>/webhook   → agent
 ├─ frontend    Angular servido por nginx
 ├─ backend     Spring Boot 3.4, Java 21
 ├─ agent       Python (FastAPI): WhatsApp + conversación + NLU (§4.11); habla con backend por la red interna
 ├─ postgres:16 sin puerto publicado; volumen del VPS
 └─ backup      pg_dump diario + copia cifrada a Google Drive de la clínica
PCs de recepción y consultorios → navegador → https://app.<dominio>
```
- **Propiedad (RNF04)**: VPS, dominio, portafolio de Meta, proveedor de IA y Google Drive con el correo y el medio
  de pago de la clínica al implantar; el desarrollador queda como colaborador. Durante el desarrollo los paga el
  desarrollador (temporal). El `docker-compose` es portable a otro proveedor.
- **Encargados del tratamiento (LOPDP)**: proveedor del VPS, Meta (Cloud API) y proveedor de IA. Se aceptan sus
  términos de tratamiento, se nombran en el aviso de privacidad (RF10) y se justifica la transferencia
  internacional (servidores fuera de Ecuador).
- **Seguridad mínima**: solo 80/443 y SSH con llave (firewall `ufw`, `fail2ban` en SSH); Postgres y backend sin
  puertos publicados; límite de intentos en `/auth/login`; actualizaciones automáticas del sistema operativo;
  secretos en `.env` fuera del repositorio.
- **Sin internet en la clínica** no se puede usar el sistema (el bot sigue atendiendo): contingencia BN10 con la
  ficha rápida en papel y carga posterior. Recomendado: datos móviles como conexión de respaldo.
- **Respaldos (D-18)**: `pg_dump -Fc` diario 20:30 en el VPS (retención 30 días) y la misma copia cifrada en el
  Google Drive de la clínica (`rclone`). Solo esas dos copias. La restauración se documenta en
  `docs/OPERATIONS.md`, pero no se prueba dentro del MVP.
- Exportación abierta: ZIP con un CSV por tabla (UTF-8, cabecera) + dump SQL, solo `ADMIN`.
- Sin licencia ni verificación remota. Se entregan `docker-compose`, `.env.example` y guía de instalación.
- Desarrollo del bot: número de prueba de Meta y webhook a la laptop con un túnel temporal
  (`cloudflared tunnel --url http://localhost:8000`).

## 2. Convenciones transversales

- API REST bajo `/api/v1`. JSON. Errores con un formato único (`shared/exception`):
  `400` validación, `401` sin sesión, `403` sin permiso, `404`, `409` conflicto (cruce de citas,
  estado inválido, duplicado).
- Autorización por permisos (`@PreAuthorize("hasAuthority('RECORD_UPDATE')")`), no por nombre de rol,
  salvo reglas explícitas ("solo ODONTOLOGO edita odontograma").
- Usuario actual: un componente en `security` que devuelve `userId` y, si existe, `professionalId`.
- Hora: se guarda `timestamptz`; se calcula y muestra en `America/Guayaquil`
  (`application.clinic.zone`). Nunca `LocalDateTime` en entidades.
- Sin `DELETE` en datos clínicos ni pagos: anular con `voided_at` (+ `void_reason` donde exista).
  Los listados excluyen anulados salvo `?includeVoided=true`.
- Paginación: `page`, `size` (máx. 50). Búsquedas por texto: mínimo 2 caracteres.
- PDFs (receta, comprobante): OpenPDF; siempre con leyenda "CONFIDENCIAL" y datos de la clínica.
- Datos de la clínica (nombre, dirección, teléfono, horarios, formas de pago) en `application.clinic.*`.
- Módulos no se importan entre sí: cada uno expone una interfaz pública (p. ej. `PatientDirectory`,
  `AppointmentScheduler`) y los demás dependen de ella.

## 3. Orden de tareas y dependencias

Tests: manda `.agents/skills/testing/SKILL.md` (un `*IT` de 2–3 casos por tarea con endpoints y máx. 4 unit
tests de reglas puras). La columna `*IT` y los tests listados en §4 son orientativos: cada tarea fija los suyos.

| Tarea | Módulo | Migración | Depende de | `*IT` |
|---|---|---|---|---|
| 001 | Esquema base identity/staff/patients | V1–V3 | — | `SchemaMigrationIT` (en correcciones) |
| 002 | Semillas base | V4 | 001 | `BaseSeedIT` |
| 003 | Auth email + JWT | — | 002 | `AuthControllerIT` |
| 004 | Pacientes y profesionales (API) | — | 003 | — |
| 004a | Tipo de sangre como etiqueta (bug 500 en alta) | — | 004 | PatientBloodTypeIT |
| 005 | Historia clínica + antecedentes | V5 | 004 | — (cubierto en 008) |
| 005b | Historia clínica con ApiResponse (G-9 del frontend) | — | 005 | `ClinicalRecordApiIT` |
| 006 | Agenda + horarios | V6 (incluye `professional_schedules`) | 004 | `AppointmentOverlapIT` |
| 006c | Horarios con `ApiResponse` (G-8 del frontend) | — | 006 | `ScheduleApiIT` |
| 006d | Citas por paciente y adminNotes (G-4, G-5 del frontend) | — | 004, 006 | `PatientRecordApiIT` |
| 006e | La cita trae su atención (G-1 del frontend) | — | 006, 008 | `AppointmentEncounterLinkIT` |
| 007 | Catálogos clínicos: CIE-10, pg_trgm | V7 (`V7__icd10_catalog`) | 005 | `Icd10SearchIT` |
| 007a | CIE-10: redacción oficial OPS y términos incluidos para la búsqueda | V14 (`V14__icd10_ops_terms`) | 007 | `Icd10SearchIT` (+1 caso) |
| 008 | Atención clínica (encounters + diagnósticos) | V7.1 (`V7_1__encounters`) | 006, 007 | `EncounterFlowIT` |
| 008b | Atención: casillas del 033 (regiones del examen, exámenes del plan) y descripción única de planes | V14.1 (`V14_1__encounter_033_marks`) | 008 | `EncounterFlowIT` (+1 caso) |
| 009 | Odontograma | V8 | 008 | `OdontogramFlowIT` |
| 010a | Tratamientos: catálogo y registro en la atención | V9 | 008, 009 | `TreatmentFlowIT` |
| 010b | Tratamientos ligados al odontograma (hallazgo + DONE automático) | — | 010a | — |
| 011a | Recetas: medicamentos, presets y ciclo DRAFT → ISSUED | V10 | 008 | `PrescriptionFlowIT` |
| 011b | Receta PDF | — | 011a | — |
| 011c | Historial clínico `clinical-summary` (RF06, RF09) | — | 010a, 011a | `ClinicalSummaryIT` |
| 011d | Receta PDF: diseño con la marca (logo, marca de agua, sello y firma, servicios, franja de contacto) | — | 011b | `PrescriptionPdfIT` (aserciones) |
| 012 | Cobros + comprobante | V15 (renumerada: V13 y V14 ya existen) | 010 | — |
| 013 | Auditoría Envers | V16 | 012 | — (añade un caso a `EncounterFlowIT`) |
| 014 | Google Calendar (sistema → Google) | — | 006 | — |
| 015a | Bot (Java): API interna base — V13, clave de servicio, consentimiento, info, identificación | V13 | 006 | `BotApiIT` |
| 015b | Agente (Python): esqueleto, webhook, firma, envío, cliente del backend | — | 015a | — (pytest) |
| 015c | Agente: aviso de privacidad, menú, horarios, precios, derivar | — | 015b | — (pytest) |
| 016a | Bot (Java): consultas de agenda — especialidades, profesionales, días, horas, urgencias, citas del paciente | — | 015a | `BotAvailabilityIT` |
| 016b | Bot (Java): agendar, reprogramar, cancelar, confirmar + límites (D-9, D-10) y `code` en los 409 | — | 016a | `BotAppointmentIT` |
| 016c | Agente: identificación del paciente y agendar | — | 015c, 016b | — (pytest) |
| 016d | Agente: mis citas — reprogramar, cancelar, confirmar | — | 016c | — (pytest) |
| 016e | Agente: NLU con LLM — intenciones, urgencia, precios, fuera de tema | — | 016d | — (pytest) |
| 017a | Bot (Java): recordatorios — creación por eventos, due/sent/response | — | 016b | `BotReminderIT` |
| 017b | Agente: envío de recordatorios y respuesta a sus botones | — | 016d, 017a | — (pytest) |
| 018a | Despliegue: imagen del backend, perfil prod, compose de producción, Caddy y CI/CD (Contabo, GHCR) | — | todo | — |
| 018b | Agente: imagen Docker y CI/CD (repo del agente) | — | 018a | — |
| 018c | Respaldos diarios (pg_dump + rclone crypt a Drive), restore.sh y `docs/OPERATIONS.md` | — | 018a | — |
| 018d | Exportación abierta: `GET /admin/export` (ZIP de CSV) | — | 018a | `AdminExportIT` |
| 018e | Preparación del VPS: `deploy/vps-setup.sh` y registro de la configuración del servidor | — | 018a | — |

(007 y 008 van separadas: CIE-10 en `V7__icd10_catalog.sql` y atención en `V7_1__encounters.sql`, para no renumerar 009+.)
(El historial inmediato `GET /patients/{id}/clinical-summary` de §4.4 sale de la 008: es la tarea 011c.)
(Las tareas del agente (015b, 015c, 016c–016e, 017b) viven en `../bellidental-agent/docs/tasks/`, con su propio
`CLAUDE.md`/`GEMINI.md`; el contrato entre ambos es `../bellidental-agent/docs/BACKEND-API.md`.)
(La 012 y el historial inmediato no bloquean al bot, pero sí el postest del TGA: deben estar listos antes de medir.)

---

## 4. Módulos

### 4.1 Pacientes y profesionales — tarea 004 (RF06 parcial, RF07 base)
Los módulos ya existen; se ajustan y se exponen solo estas operaciones.

**Pacientes** (`com.bellidental.patients`)
- `POST /patients` (PATIENT_CREATE): cédula, nombres, fecha de nacimiento, sexo obligatorios; teléfono
  normalizado a E.164 sin "+" (`0987654321` → `593987654321`). Cédula ecuatoriana validada (dígito
  verificador) cuando `document_type = CI`. `admin_notes` opcional (máx 1000 caracteres, recortado).
- `PUT /patients/{id}` (PATIENT_UPDATE). `admin_notes` editable por recepción (máx 1000 caracteres, trim; vacío borra la nota).
- `GET /patients?query=` (PATIENT_READ): por cédula exacta, prefijo de teléfono o nombre (ILIKE ahora;
  trigram cuando exista el índice de V7).
- `GET /patients/{id}`: identificación + edad calculada + `adminNotes`. Nunca datos clínicos.
- Interfaz pública `PatientDirectory` (buscar por id, por teléfono, por documento) para agenda, bot y cobros.
- Desactivar = `active=false`; no hay borrado.

**Profesionales** (`com.bellidental.professionals`)
- `GET /professionals` (PROFESSIONAL_READ) con especialidades activas y `color_tag`.
- `PUT /professionals/{id}` (PROFESSIONAL_MANAGE): datos, color, `google_calendar_id`, especialidades.
- Interfaz pública `ProfessionalDirectory`.

Unit: normalización E.164; validación de cédula; documento duplicado → conflicto.

### 4.2 Historia clínica y antecedentes — tarea 005 (RF01)
Paquete `com.bellidental.clinicalrecords`. Migración V5 con `clinical_records`,
`medical_history_conditions` (semilla de los 10 ítems del 033 en su orden: alergia a antibióticos,
alergia a anestesia, hemorragias, VIH/SIDA, tuberculosis, asma, diabetes, hipertensión, enfermedad
cardíaca, otro) y `medical_history_entries`.

Reglas:
- Una historia por paciente. Se abre automáticamente en la primera atención (o explícitamente) por un
  odontólogo: `opened_by` = su profesional, `opened_at` = hoy, `record_number` por secuencia
  `HC-000001` (D-5: identificador provisional; si la clínica confirma que debe ser la cédula, se
  migra con una actualización de datos, sin cambiar columnas).
- Solo `ODONTOLOGO` (RECORD_CREATE/UPDATE) crea, edita o anula antecedentes.
- Entrada `FAMILY` exige `relative`; `PERSONAL` lo prohíbe. Condición "Otro" exige `description`.
- Marcar `no_known_history=true` con entradas activas → 409; añadir una entrada lo pone en `false`.
- Corregir = anular (`voided_at`) y crear otra.

Endpoints: `GET/POST /patients/{id}/clinical-record`, `POST /clinical-records/{id}/history-entries`,
`POST /history-entries/{id}/void`, `PUT /clinical-records/{id}` (medicación actual, no refiere).
Todos responden con `ApiResponse` (tarea 005b; antes devolvían el DTO sin envoltorio). En el `PUT`, `currentMedication` ausente o null no cambia; con texto se guarda con trim; vacía o solo espacios se guarda como null (se borra).
Unit: reglas de scope/relative, no_known_history, apertura única.

### 4.3 Agenda — tarea 006 (RF07, reprogramar en recepción; base de RF12/RF15)
Paquete `com.bellidental.appointments`. Migración V6: `appointments` con CHECK de status/source,
`CHECK (end_at > start_at)`, `btree_gist` y el `EXCLUDE` de las notas SQL; y `professional_schedules`.

**Horario del profesional (D-1)** — `professional_schedules`:
- Planta: filas semanales (`weekday`), una por turno (p. ej. lunes 08–12 y lunes 14–20).
- Especialista ocasional: filas por fecha (`specific_date`), de pocas horas a todo el día.
- Excepción de un día: si existen filas con `specific_date = D` para el profesional, **reemplazan**
  a las semanales de ese día. Una fila con `available=false` significa que ese día no atiende.
- Horario efectivo de un día = filas por fecha si existen; si no, las semanales; menos los turnos con
  `available=false`.
- No se permiten turnos solapados del mismo profesional el mismo día (validación en el servicio).
- Endpoints: `GET /professionals/{id}/schedules` (turnos registrados),
  `GET /professionals/{id}/schedules/effective?from&to` (horario efectivo por día: `{date, shifts:[{startTime, endTime}]}`),
  `POST /professionals/{id}/schedules`, `PUT /schedules/{id}`, `POST /schedules/{id}/deactivate`.
  Todos responden con `ApiResponse` (tarea 006c; antes devolvían el DTO sin envoltorio).
  Permiso: SCHEDULE_MANAGE (ADMIN y SECRETARIA; D-12). La secretaria registra días y horas de cada profesional.

**Estados:**
```
PENDING ──confirmar──▶ CONFIRMED
   │  └──────────┬──────────┘
   │        check-in (checked_in_at) ──▶ atención cerrada ──▶ ATTENDED
   ├──cancelar──▶ CANCELLED
   ├──reprogramar──▶ RESCHEDULED (+ cita nueva con rescheduled_from_id)
   └──no asistió──▶ NO_SHOW
```
- `ATTENDED` solo lo pone el módulo clínico al completar la atención.
- `CANCELLED`, `NO_SHOW`, `RESCHEDULED`, `ATTENDED` son finales.
- `NO_SHOW`: lo marca recepción a mano (D-7). Automatizarlo se estudiará cuando la clínica haga el
  check-in de forma constante.

**Reglas:**
- `end_at` por defecto = `start_at + specialties.default_duration_minutes`.
- El profesional debe tener la especialidad activa (FK compuesta).
- Cruce → la BD lanza la violación del EXCLUDE; se traduce a `409 APPOINTMENT_OVERLAP`. No validar
  solo en Java: la BD es la garantía.
- La cita debe caer dentro del horario efectivo del profesional, salvo `WALK_IN` y
  `source=PROFESSIONAL` (el doctor puede abrir fuera de horario).
- Walk-in: `POST /appointments/walk-in` crea cita `WALK_IN` en el primer hueco libre desde ahora con
  `checked_in_at=now`; si no hay hueco → 409 y recepción elige otro profesional/hora (O4).
- Reprogramar (`POST /appointments/{id}/reschedule` con nueva hora): en una transacción, original →
  `RESCHEDULED`, nueva cita con `rescheduled_from_id`, mismo paciente/profesional/especialidad salvo
  que se indique otro. Solo desde PENDING/CONFIRMED. Lo usan recepción y el bot.
- `source`: RECEPTION (SECRETARIA), PROFESSIONAL (ODONTOLOGO), BOT, WALK_IN. `created_by` = usuario actual.
- Publica eventos de dominio (`AppointmentCreated/Rescheduled/Cancelled/StatusChanged`) que consumen
  Google Calendar y recordatorios. Así agenda no depende de ellos.
- Disponibilidad: `GET /availability?professionalId&specialtyId&date` (opcional `&durationMinutes`) → huecos
  libres (horario − citas activas), en pasos de la duración indicada o de la especialidad si se omite (tarea 006f).
  La usan recepción y bot.
- Límites de duración (tarea 006f): `durationMinutes` en `/availability` y `endAt − startAt` en `POST /appointments`
  y `POST /appointments/{id}/reschedule` deben ser múltiplos de 15 minutos, entre 15 y 240 (400 "La duración debe ser
  múltiplo de 15 minutos, entre 15 y 240."). Si `endAt` no llega, toma la duración por defecto de la especialidad.

Endpoints: `GET /appointments` (`?from&to`, `?professionalId&from&to`, `?patientId` o `?patientId&from&to`),
`POST /appointments`, `POST /{id}/confirm|cancel|check-in|no-show|reschedule`, `POST /walk-in`,
`GET /availability`.
Filtros de `GET /appointments` (tarea 006d, para la ficha de recepción del paciente):
- Sin `patientId`: `from` y `to` obligatorios (400 "Indica 'from' y 'to'."), `to > from`, máx. 31 días.
- `?patientId` sin rango: todas las citas del paciente, de todos los estados, por `startAt` ascendente;
  paciente sin citas o inexistente → `200` con `data: []`.
- `?patientId&from&to`: paciente y rango, con las mismas validaciones. Solo uno de `from`/`to` → 400.
- `patientId` con `professionalId` → 400 (ninguna pantalla lo usa).
Atención vinculada en `AppointmentResponse` (tarea 006e, para "Mis citas de hoy"): `encounterId` (`uuid` o `null`), `encounterStatus` (`OPEN`/`COMPLETED` o `null`) y `encounterVoided` (`boolean`, si `voided_at != null`). Sin atención (recién creada o reprogramada): `null`, `null`, `false`. Con atención: datos de `encounters` vía `EncounterRepository` (lectura en lote, sin N+1, D-15).

Unit: transiciones válidas/inválidas, cálculo de `end_at`, horario efectivo (semanal, por fecha,
`available=false`), cálculo de huecos, reprogramar.
IT `AppointmentOverlapIT`: dos citas cruzadas del mismo profesional → 409; tras cancelar la primera,
la segunda entra.

### 4.4 Atención clínica + CIE-10 — tareas 007–008 (RF01, RF03, RF06)
Paquete `com.bellidental.clinical`. Migración V7: `pg_trgm`, `icd10_codes` con semilla (capítulo
odontológico K00–K14 completo + Z01.2 y códigos que usa la clínica de su hoja), índices GIN y
`text_pattern_ops`, índice trigram de nombre de paciente (O9), `encounters`, `encounter_diagnoses`.

**CIE-10:** `GET /icd10?query=` — si parece código (`^[A-Za-z]\d`) busca por prefijo de código; si no,
por similitud de descripción. Máx. 20 resultados, activos.
**CIE-10 OPS (007a, V14):** títulos con la redacción oficial en español de la OPS que usa la clínica
(se conservan K02.5, K12.3 y Z01.2). Columna `included_terms` (términos incluidos, p. ej. «Fluorosis dental» en
K00.3) con índice trigram: la búsqueda por texto mira `description` e `included_terms`; la descripción sigue
corta (solo el título) en la receta, el historial y el 033. Las exclusiones («Excluye») no se guardan.

**Atención:**
- `POST /appointments/{id}/encounter` (ODONTOLOGO): solo si la cita tiene check-in y no tiene atención;
  `professional_id` = profesional del usuario actual (quien atiende, puede diferir del agendado);
  abre la historia clínica si no existe.
- `PUT /encounters/{id}`: secciones 1, 2, 4, 5, 7, 10 del 033 (`stomatognathicRegions` 1–12,
  `diagnosticExams` BIOMETRIA/QUIMICA_SANGUINEA/RAYOS_X/OTROS, `planDescription`). Quién y cuándo puede
  editar: ver **Cierre (D-6)**.
- `POST /encounters/{id}/diagnoses` (icd10, PRESUMPTIVE/DEFINITIVE, notas); duplicado activo → 409.
- `POST /encounters/{id}/complete`: exige `chief_complaint` y ≥1 diagnóstico; `status=COMPLETED`,
  `end_at=now`; cita → `ATTENDED`.
- **Cierre (D-6)**: al completar se fijan `status=COMPLETED` y `closed_at=now` a la vez. Desde ahí no
  se edita nada de la atención en sitio. Correcciones:
  - Ítems (diagnósticos, hallazgos, tratamientos, recetas): anular el incorrecto con motivo
    (`voided_at`, `void_reason` donde exista) y registrar el correcto **en la misma atención**
    (se permite añadir ítems a una atención cerrada, solo para corregir, solo el odontólogo que atendió).
  - Campos de cabecera (motivo, signos, examen, marcas 033, planDescription): no tienen anulación por campo. Se permite
    corregirlos solo al odontólogo que atendió; Envers conserva el valor original, quién y cuándo
    cambió (no es una sobrescritura silenciosa).
  - Anular la atención entera (`void_reason`) es para atenciones registradas por error. Como
    `encounters.appointment_id` es único, la atención correcta va en otra cita (WALK_IN con la fecha real).
- Anular atención: `void_reason` obligatorio; sus hijos dejan de contar (saldo, odontograma, CPO).
- Solo el odontólogo; la secretaria recibe 403 en todo `/encounters`, `/clinical-records`, `/icd10`.

**Historial inmediato (RF06):** `GET /patients/{id}/clinical-summary` (ODONTOLOGO): antecedentes
activos, medicación, odontograma vigente, tratamientos pendientes (hallazgos rojos vigentes) y
línea de tiempo de sesiones (N° sesión, fecha, profesional, diagnósticos, procedimientos, recetas).
Debe responder en <1 s con 1000 pacientes.
**Ajuste (2026-10-19, tarea 011c):** módulo propio `clinicalsummary` (evita el ciclo clinical ↔ treatments/prescriptions).
Devuelve solo la cabecera de la historia (`clinicName`, `recordNumber`, `openedAt`, `openedBy` con `licenseNumber`)
y las sesiones (atenciones no anuladas, numeradas por `startAt`; recetas solo ISSUED). Antecedentes, odontograma y
pendientes salen de sus endpoints. Alimenta la ficha clínica y la impresión del 033 (RF09, ahora en el MVP).

Unit: reglas de apertura/completado, detección código vs texto, numeración de sesiones.
IT `EncounterFlowIT`: check-in → abrir → diagnóstico → completar → cita ATTENDED;
la secretaria recibe 403 al abrir atención.

### 4.5 Odontograma — tarea 009 (RF02)
Paquete `com.bellidental.odontogram`. Migración V8 (D-14): `teeth` (52 piezas FDI), `tooth_surfaces`
(O, V, L, M, D), `dental_conditions` y `dental_condition_symbols` (simbología por condición y estado),
con semilla según `proy-infor/otondograma-completo.txt` y las filas del 033. Detalle en `docs/tasks/009-odontograma.md`.

| code | category | scope | grado | cpo | ceo | PENDING (rojo) | DONE (azul / negro) |
|---|---|---|---|---|---|---|---|
| MOBILITY | PERIODONTAL | TOOTH | 0–3 (Miller) | — | — | — | número |
| RECESSION | PERIODONTAL | TOOTH | 1–4 (Miller I–IV) | — | — | — | número |
| CARIES | DENTAL | SURFACE | — | C | c | círculo | — |
| FILLING | DENTAL | SURFACE | — | O | o | — | círculo |
| SEALANT | DENTAL | SURFACE | — | — | — | asterisco | asterisco |
| EXTRACTION | DENTAL | TOOTH | — | P | e | X | — |
| MISSING_CARIES | DENTAL | TOOTH | — | P | — | — | X |
| MISSING_OTHER | DENTAL | TOOTH | — | — | — | — | círculo + X negra |
| ROOT_CANAL | DENTAL | TOOTH | — | — | — | triángulo | triángulo |
| CROWN | DENTAL | TOOTH | — | O | o | — | cuadrado doble |
| FIXED_PROSTHESIS | DENTAL | SPAN | — | — | — | — | línea punteada con cuadrados (negro) |
| REMOVABLE_PROSTHESIS | DENTAL | SPAN | — | — | — | — | (···) (negro) |
| TOTAL_PROSTHESIS | DENTAL | SPAN | — | — | — | — | = (negro) |
| OTHER | DENTAL | TOOTH | — | — | — | rectángulo relleno | rectángulo relleno |

(Revisar con la odontóloga: CPO de CROWN y si corona/prótesis "por hacer" se marcan con OTHER rojo.
Caries simple/compuesta/compleja = 1, 2, 3+ caras con CARIES vigente, calculado.)

Reglas:
- Solo el odontólogo que atendió, en atención no anulada: `OPEN`, o `COMPLETED` para corregir (anular el
  hallazgo y registrar el correcto en la misma atención, D-6). Regla única en
  `ClinicalDirectory.requireModifiableEncounter`, que reusan 009, 010 y 011. SURFACE exige cara; TOOTH y SPAN la prohíben; SPAN exige
  `end_tooth_code` de la misma arcada. Grado obligatorio y en rango si la condición lo define; prohibido si no.
  El par (condición, estado) debe tener símbolo. OTHER exige `notes`. Piezas temporales (51–85) permitidas.
- **Estado vigente (O3)**: último hallazgo no anulado, de atenciones no anuladas, por (pieza, cara) en
  SURFACE, por (pieza, condición) en TOOTH y por (pieza, pieza final, condición) en SPAN. Una pieza con
  MISSING_* vigente oculta sus demás hallazgos de pieza y cara (no los SPAN).
- CPO/ceo del estado vigente; cada pieza cuenta una vez por componente.
- Etiqueta de cara por pieza: L = Palatina en arcada superior, Lingual en inferior; O = Incisal en
  incisivos y caninos. Mesial/distal se dibujan según cuadrante (frontend).

Endpoints: `GET /odontogram/catalog` (piezas, caras, condiciones con símbolos), `GET /patients/{id}/odontogram`
(vigente + CPO/ceo), `GET /encounters/{id}/findings`, `POST /encounters/{id}/findings`, `POST /findings/{id}/void`.

### 4.6 Tratamientos — tareas 010a y 010b (RF04)
Paquete `com.bellidental.treatments`. Migración V9: `treatment_catalog` con semilla (códigos de su
ficha: D0150 examen, D1110/D1330 profilaxis/higiene, resinas, curaciones, exodoncia, endodoncia,
sellantes…, con `base_price` que dé la clínica) y `performed_treatments`.

Reglas:
- Registra el ODONTOLOGO en la atención OPEN. `price` se copia de `base_price` (editable para
  descuentos); `quantity ≥ 1`.
- Puede ligarse a un diagnóstico y/o a un hallazgo de la misma atención o de una anterior del mismo
  paciente. Si se liga a un hallazgo PENDING, crear en la atención actual el hallazgo DONE
  correspondiente (pasa a azul).
- Anular exige `void_reason`; afecta al saldo.
- `GET /treatments?query=` búsqueda por nombre/código.
- Interfaz pública `ChargeLedger` para cobros (pasa a la tarea 012, la que la consume): cargos por paciente (fecha, tratamiento, profesional,
  valor) **sin diagnósticos**.
Unit: precio por defecto, validación de pertenencia del hallazgo, creación del DONE.

### 4.7 Recetas — tarea 011 (RF04, RF05)
Paquete `com.bellidental.prescriptions`. Migración V10: `medications` + `medication_presets` con
semilla (amoxicilina, ibuprofeno, paracetamol, clindamicina, ketorolaco, clorhexidina… con sus
presentaciones y posologías "cada 8 h por 5 días", etc.), `prescriptions`, `prescription_items`.

Reglas:
- `GET /medications?query=` autocompletar desde la primera letra (prefijo + trigram), con presets.
- Crear receta en atención (ODONTOLOGO), ligada a un diagnóstico de esa atención. Ítems: elegir
  preset copia dosis/frecuencia/duración (editables); `quantity` total (p. ej. "21 tabletas").
- `DRAFT` editable; `POST /prescriptions/{id}/issue` → `ISSUED`, inmutable (corregir = anular y crear).
- `GET /prescriptions/{id}/pdf` (solo ISSUED): clínica, "CONFIDENCIAL", paciente (nombre, cédula,
  edad), fecha, CIE-10 + descripción, ítems, indicaciones generales, profesional + registro, línea
  de firma y sello.
Unit: copia de preset, transición DRAFT→ISSUED, inmutabilidad, diagnóstico de otra atención → error.
PDF: un test que genera bytes no vacíos y contiene "CONFIDENCIAL".

### 4.8 Cobros — tarea 012 (RF08)
Paquete `com.bellidental.billing`. Migración V15: `payments`, secuencia de comprobante
(`receipt_number` = `C-000001`), CHECK `amount > 0`, REVOKE DELETE (notas SQL).

Reglas:
- SECRETARIA (BILLING_CREATE) registra abono: monto, método, opcionalmente la atención de la sesión.
- **Saldo** = Σ(price × quantity) de tratamientos no anulados en atenciones no anuladas
  − Σ pagos no anulados. Nunca se guarda. Puede ser negativo (saldo a favor).
- `GET /patients/{id}/account` (BILLING_READ): estado de cuenta tipo ficha rápida — por sesión:
  fecha, tratamientos, profesional, cargo, abono, saldo acumulado. Sin diagnósticos.
- `GET /payments/{id}/receipt` PDF: "Comprobante interno — no válido como factura", "CONFIDENCIAL",
  paciente, detalle, abono, saldo, línea de firma del paciente.
- Anular pago (D-8): ADMIN y SECRETARIA (es quien cobra), con `void_reason` obligatorio; queda en
  Envers. Usa el permiso BILLING_CREATE (no hace falta tocar la semilla).
Unit: cálculo de saldo (anulados excluidos, pago sin atención, saldo a favor), secuencia.

### 4.9 Auditoría — tarea 013 (RNF02)
- Envers `@Audited` en: clinical_records, medical_history_entries, encounters, encounter_diagnoses,
  dental_findings, performed_treatments, prescriptions, prescription_items, payments, appointments,
  patients. V16 crea `revision_info` y las tablas `*_aud`.
- `RevisionListener` guarda `user_id` del usuario autenticado (bot → `bot_whatsapp`; jobs → null).
- Spring Data Auditing mantiene `created_at/updated_at`.
- `GET /audit/{entity}/{id}` (ADMIN): quién, cuándo, qué cambió.
IT: añadir a `EncounterFlowIT` que la revisión registra al odontólogo.

### 4.10 Google Calendar — tarea 014 (RF07, una vía)
Paquete `com.bellidental.calendar`. La agenda del sistema es la única fuente; Google es un espejo
para que el director vea su día en el móvil. **No** se lee de Google (eso reintroduciría PC7).

- Cuenta de servicio; cada `professionals.google_calendar_id` es un calendario compartido con ella con
  permiso de edición. Credenciales por variable de entorno; `application.google-calendar.enabled`.
- Escucha eventos de agenda **después del commit** y en asíncrono: crear (guarda `google_event_id`),
  actualizar hora, borrar el evento al cancelar/reprogramar/no-show (la cita nueva crea el suyo).
- Contenido mínimo (BN9): título "Cita — <Nombre A.>", especialidad, sin motivo ni datos clínicos.
  Color del evento ≈ `color_tag`.
- Fallo de Google: se registra en log y no afecta a la cita. Reconciliación diaria 06:00 (y al
  arrancar) de las citas activas de los próximos 30 días: crea las que no tienen `google_event_id`,
  corrige hora (idempotente). Así no se necesita columna de estado (O8).
Unit con cliente mockeado: mapeo cita→evento, reconciliación decide crear/actualizar/borrar.

### 4.11 Bot WhatsApp — tareas 015a–017b (RF10–RF15, RNF03)

**Arquitectura híbrida (D-19).** Dos piezas; el agente nunca toca la BD:

| Pieza | Dónde | Hace | No hace |
|---|---|---|---|
| Agente (Python, FastAPI) | repo `../bellidental-agent` | webhook de WhatsApp (verificación y firma), envío por Graph API, estado de la conversación, NLU con LLM, textos | acceder a la BD, decidir reglas de negocio |
| API interna del bot (Java) | este repo, módulo `com.bellidental.bot` | `/api/v1/bot/*`: consentimiento, info, identificación, disponibilidad, agendar/reprogramar/cancelar/confirmar, recordatorios; aplica D-9, D-10 y RNF03 | conversación, WhatsApp |

Principio: **la IA interpreta, el código decide**. El LLM solo devuelve intención y datos; la máquina de estados del
agente decide el paso siguiente; toda escritura la hace Java y solo después de que el paciente pulse *Confirmar*.
El LLM no tiene herramientas que escriban ni ve datos de otros pacientes (defensa ante *prompt injection*).
Hay dos tipos de estado: los de la **cita** (PENDING, CONFIRMED…) siguen en Java (§4.3); los de la
**conversación** (paso del flujo) viven en el agente.

**Cuenta de WhatsApp (D-3, D-22, no negociable)** — sin verificación de empresa ante Meta:
| Entorno | Modalidad | Uso |
|---|---|---|
| Desarrollo | Número de prueba de Meta: gratis, solo a destinatarios registrados a mano (máx. 5) | Desarrollo y demos |
| Producción | Chip prepago dedicado conectado a Cloud API **sin verificación de empresa** | Pacientes reales |

- Límites confirmados: 250 números distintos por 24 h móviles para los mensajes que inicia la clínica
  (plantillas), a nivel de portafolio; las respuestas dentro de la ventana de 24 h no cuentan; máx. 2 números.
- El número del bot es exclusivo: no puede usarse en la app de WhatsApp. Recepción sigue con su número actual.
- Política de Meta (desde el 15-ene-2026): prohibidos los asistentes de IA de propósito general. El bot solo
  trata temas de la clínica; fuera de tema → texto fijo + menú.
- Número, token, App Secret e id de plantilla van por configuración del agente; el código no cambia entre entornos.
- Pasos de alta y tareas del desarrollador: `docs/WHATSAPP-SETUP.md`.

**API interna (Java, `com.bellidental.bot`)**
- Autenticación: header `X-Bot-Key` = `application.bot.api-key` (variable de entorno). Un filtro en `security`
  autentica como `bot_whatsapp` con la autoridad `BOT` (en código; sin cambios en la semilla). `/api/v1/bot/**`
  exige `BOT` y la clave no sirve en ningún otro endpoint. Caddy no expone `/api/v1/bot/**`.
- Pertenencia: toda operación sobre una cita recibe `phone` y valida que el paciente de la cita tenga ese
  `phone_e164`; si no → 404.
- Usa `PatientDirectory`, `AppointmentScheduler`, `ProfessionalDirectory`, precios de `treatment_catalog` y
  `application.clinic.*`. **Nunca** módulos clínicos ni cobros (RNF03). Respuestas con `ApiResponse`.
- Configuración nueva: `application.bot.public-treatments` (códigos de `treatment_catalog` con precio público),
  `application.bot.urgent-specialty-id`, `application.bot.limits.*` (D-9), `application.clinic.reception-whatsapp`,
  `application.clinic.after-hours-phone`, `application.bot.privacy-notice-version`. Sin cambios de esquema
  (V13 crea `privacy_consents` y `appointment_reminders` tal como están en `schema.dbml`).

| Método y ruta | Uso | Tarea |
|---|---|---|
| `GET /bot/consents?phone` | ¿aceptó la versión vigente del aviso? | 015a |
| `POST /bot/consents` `{phone, patientId?}` | guarda la aceptación | 015a |
| `GET /bot/info` | horarios, formas de pago, enlace de recepción, teléfono fuera de horario, precios públicos | 015a |
| `GET /bot/patients?phone` | pacientes activos del número: `{patientId, displayName: "Juan P."}` | 015a |
| `POST /bot/patients/verify` `{phone, idNumber}` | desambigua por cédula dentro de ese grupo | 015a |
| `GET /bot/specialties` · `GET /bot/professionals?specialtyId` | opciones para agendar | 016a |
| `GET /bot/availability/days?specialtyId&professionalId?` | próximos 14 días con huecos | 016a |
| `GET /bot/availability?specialtyId&professionalId?&date` | huecos del día (máx. 10) | 016a |
| `GET /bot/urgent-slots` | 3 primeros huecos de hoy o mañana en la especialidad de urgencias (`noSlotsToday` = fuera de horario) | 016a |
| `GET /bot/appointments?phone&patientId` | citas activas futuras del paciente | 016a |
| `POST /bot/appointments` `{phone, patientId, specialtyId, professionalId, startAt}` | crea `PENDING`, `source=BOT`; 409 `APPOINTMENT_OVERLAP` o `BOT_LIMIT_REACHED` | 016b |
| `POST /bot/appointments/{id}/reschedule` `{phone, startAt}` | reprograma; 409 `BOT_TOO_LATE` a menos de 2 h | 016b |
| `POST /bot/appointments/{id}/cancel` · `/confirm` `{phone}` | cancelar (D-10) o confirmar | 016b |
| `GET /bot/reminders/due` | recordatorios `PENDING` con `scheduled_at ≤ now` | 017a |
| `POST /bot/reminders/{id}/sent` `{waMessageId}` · `/failed` | resultado del envío | 017a |
| `POST /bot/reminders/response` `{waMessageId, response}` | respuesta del botón | 017a |

**Agente (Python, `../bellidental-agent`)** — repo propio con su `CLAUDE.md`/`GEMINI.md`, como el frontend.
```
bellidental-agent/
  app/
    main.py        FastAPI: GET /webhook (hub.verify_token), POST /webhook (firma), GET /health
    config.py      variables de entorno
    whatsapp/      envío (texto, botones, listas, plantillas) y verificación de X-Hub-Signature-256
    backend/       cliente de /api/v1/bot/* con X-Bot-Key
    dialog/        máquina de estados de la conversación y manejadores por flujo
    nlu/           interfaz classify(text, context) -> Interpretation; un adaptador por proveedor
    messages/      textos en español y fechas ("martes 14 de octubre, 10:30")
    reminders/     job cada 5 min
  tests/
  Dockerfile
```
Recorrido de cada mensaje:
1. Verificar la firma; ignorar duplicados (`message.id`).
2. Filtros fijos, sin IA: ¿aceptó el aviso? ¿hay derivación activa? ¿es un botón o una lista? → si es botón o
   lista, va directo a la máquina de estados.
3. Texto libre → NLU: `{intent, slots: {specialty, treatment, dateHint, timeHint}}`.
4. La máquina de estados decide el paso, pide lo que falta y consulta la API interna.
5. Agendar, reprogramar o cancelar solo tras el botón *Confirmar*. Precios y horas los escribe el código con los
   datos de la API; la IA nunca inventa una cifra.

| Intención | Acción |
|---|---|
| `HOURS`, `PRICE` | texto armado desde `/bot/info`; precio del catálogo + "valor referencial; el definitivo lo da el odontólogo tras la valoración"; tratamiento no reconocido → lista de precios públicos |
| `BOOK`, `RESCHEDULE`, `CANCEL`, `CONFIRM` | entra al flujo de 016c/016d con los datos extraídos; pide lo que falte |
| `URGENT` | D-21: `/bot/urgent-slots` → elige y confirma → cita; fuera de horario además el teléfono de la doctora; siempre el texto fijo de emergencia |
| `CLINICAL`, `BALANCE`, `HUMAN` | derivar |
| `OTHER` (fuera de tema) | texto fijo "Solo puedo ayudarte con citas e información de Bellidental" + menú |
| sin reconocer dos veces seguidas | derivar |

Texto fijo de emergencia (D-21): "Si tiene hinchazón que le dificulte respirar o tragar, fiebre alta o un sangrado
que no se detiene, acuda a emergencias o llame al 911." El bot nunca da consejo médico.

**NLU (D-20)**: proveedor por elegir al llegar a 016e (interfaz intercambiable; adaptador para la API compatible con OpenAI). Salida estructurada validada
(pydantic). Al LLM solo van el texto del mensaje, el paso actual y los últimos mensajes de la conversación; nunca
nombre, cédula ni historial. Proveedor que no entrene con los datos de la API y con términos de tratamiento de
datos. Si el LLM falla o tarda más de 8 s → se muestra el menú: el bot funciona sin IA.

**Estado de conversación (O7)**: en memoria del agente, por teléfono, TTL 30 min: paso actual, paciente
identificado, selección parcial, últimos mensajes y `handoffUntil`. Una sola instancia.

**015a (Java) — API base**: V13; filtro `X-Bot-Key`; consentimiento, info e identificación.
**015b–015c (Python) — núcleo del agente**
- Aviso de privacidad (RF10): sin consentimiento vigente para el teléfono, primero el aviso (nombra a Meta, al
  proveedor del VPS y al de IA) con botones *Acepto / No acepto*. Sin aceptar no se procesa nada más.
- Menú (lista interactiva): Horarios · Precios y formas de pago · Agendar · Reprogramar · Hablar con recepción.
- Derivar (RF14): mensaje con el horario y el enlace `https://wa.me/<recepción>`; `handoffUntil` = +4 h (el bot
  no responde a ese número salvo que elija una opción del menú). Sin bandeja ni tablas nuevas.
- Identificación: 0 pacientes con ese teléfono → derivar (paciente nuevo). 1 → "¿La cita es para Juan P.?" Sí/No.
  Varios → pedir cédula y validarla con `/bot/patients/verify`.

**016a–016b (Java) / 016c–016d (Python) — agendar (RF12) y reprogramar (RF15)**
- Agendar: especialidad → profesional (o "el primero disponible") → día (próximos 14 días con huecos) → hora
  (máx. 10 opciones) → confirmar. Si al confirmar el hueco ya se ocupó (409), se avisa y se ofrecen otros.
- Límites del bot (D-9), configurables y validados en Java:
  - Cita **activa** = `status IN (PENDING, CONFIRMED)` y `start_at > now`. No cuentan CANCELLED, ATTENDED, NO_SHOW
    ni RESCHEDULED (la cita que reemplaza a una reprogramada sí cuenta).
  - Máx. 1 cita activa creada por el bot por paciente y especialidad.
  - Máx. N citas activas creadas por el bot por teléfono, sumando sus pacientes (por defecto 3).
  - Al superar un límite: se explica y se deriva. Recepción no tiene estos límites.
- Reprogramar: citas activas del paciente → elige → huecos del mismo profesional y especialidad → confirma →
  operación de reprogramar de la agenda (original `RESCHEDULED`, nueva con `rescheduled_from_id`; conserva el `source` de la original).
  A menos de 2 h (D-10) → se deriva.

**016e (Python) — NLU**: intenciones y datos de la tabla anterior, urgencia, precios y fuera de tema.

**017a (Java) / 017b (Python) — recordatorios (RF13)**
- Java, al crearse una cita activa (evento de agenda): `appointment_reminders` `DAY_BEFORE`, `scheduled_at` = día
  anterior 09:00 local; si ya pasó, no se crea. Al cancelar/reprogramar/no-show: el `PENDING` pasa a `CANCELLED`;
  la cita nueva crea el suyo.
- Agente, job cada 5 min: `GET /bot/reminders/due` → envía la plantilla `recordatorio_cita` (*utility*) con
  botones *Confirmar / Cancelar / Reprogramar* → `sent` con `wa_message_id`; error → `failed` (1 reintento).
- Respuesta (por `wa_message_id`), siempre guardada (`RESPONDED`, `response`, `responded_at`) (D-2): Confirmar →
  cita `CONFIRMED`; Cancelar → cita `CANCELLED`; Reprogramar → `RESCHEDULED` y se abre el flujo de 016d (si lo
  abandona, la cita sigue igual). Cancelar o reprogramar solo hasta 2 h antes (D-10); después → se deriva.

**Tests**: Java, un `*IT` por tarea con endpoints (`BotApiIT`, `BotAppointmentIT`, `BotReminderIT`), según
`testing/SKILL.md`. Python (pytest): solo unit tests de la máquina de estados y de la verificación de firma, con el
backend y el LLM simulados; máx. 4 por tarea; nunca llamadas reales al LLM ni a Meta. El conjunto de evaluación
del NLU queda como recomendación (D-23).

### 4.12 Despliegue en VPS, respaldos y exportación — tareas 018a–018d (RNF04)
Proveedor: Contabo Cloud VPS 6 GB, Ubuntu 24.04; imágenes en GHCR; CI/CD con GitHub Actions por repo (frontend: tarea 022). Pasos manuales: `docs/DEPLOY-SETUP.md`.
- `docker-compose` de producción con `caddy`, `frontend`, `backend`, `agent`, `postgres:16` (D-11) y `backup`;
  `Caddyfile` con los dominios de §1 y el bloqueo de `/api/v1/bot/**`.
- Servicio `backup` (imagen postgres:16 + cron): `pg_dump -Fc` diario, retención 30 días en el VPS y copia cifrada
  a Google Drive con `rclone` (D-18). Script `scripts/restore.sh` documentado, sin prueba de restauración.
- `GET /admin/export` (ADMIN): ZIP con CSV por tabla (incluye `*_aud`), generado en streaming.
- Doc `docs/OPERATIONS.md`: instalar en el VPS, dominio y HTTPS, firewall, respaldar, restaurar, exportar, rotar
  secretos (JWT, `X-Bot-Key`, token de WhatsApp).
Unit: escritor CSV (escapes, UTF-8, cabeceras).

### 4.13 Cuentas de acceso para profesionales — **posterior al MVP** (D-13)
Hoy no existe alta de usuarios por API (`/auth/register` se cerró en la tarea 003; `UserController` solo lista,
edita y hace borrado lógico, para ADMIN). Mientras no se implemente, **una cuenta nueva se crea con una
migración Flyway** como las de la semilla (V-siguiente, UUID fijo, rol ODONTOLOGO, contraseña temporal en BCrypt).

Endpoints estipulados para cuando se implemente:
| Método y ruta | Permiso | Cuerpo | Efecto |
|---|---|---|---|
| `POST /api/v1/professionals/{id}/access` | ADMIN (`PROFESSIONAL_MANAGE`) | `{email, temporaryPassword}` | Crea `users` (username = parte local del email), rol ODONTOLOGO, enlaza `professionals.user_id`. 409 si el email existe o el profesional ya tiene cuenta |
| `POST /api/v1/users/{id}/reset-password` | ADMIN | `{temporaryPassword}` | Reemplaza el hash; no revela la contraseña anterior |
| `PUT /api/v1/auth/password` | autenticado | `{currentPassword, newPassword}` | Cambia la propia contraseña (mín. 10 caracteres); 401 si la actual no coincide |
| `POST /api/v1/users/{id}/deactivate` | ADMIN | — | `active=false`; reemplaza al `DELETE /users/{id}` actual |

Reglas: no se crean ADMIN ni SECRETARIA por API (siguen fijos por semilla); nunca se registran contraseñas en logs;
forzar el cambio de la contraseña temporal requeriría una columna `must_change_password` (cambio de esquema, decidir entonces).

---

## 5. Matriz rol × módulo

| | ADMIN | ODONTOLOGO | SECRETARIA | bot |
|---|---|---|---|---|
| Pacientes (identificación) | CRUD | lectura | CRUD | lectura mínima |
| Agenda | todo | todo | todo | crear, reprogramar, confirmar, cancelar |
| Horarios de profesionales | sí | lectura | **registra** | lectura (disponibilidad) |
| Historia, atención, odontograma, recetas | lectura* | todo | **nada** | **nada** |
| Tratamientos | lectura* | registrar | solo vía estado de cuenta | precios del catálogo |
| Cobros | todo + anular | nada | registrar, consultar, anular | nada |
| Auditoría, exportación | sí | no | no | no |

\* El director tiene además `ODONTOLOGO`. Si se quiere que ADMIN sin ODONTOLOGO no vea clínico,
quitar RECORD_* de ADMIN en la semilla (hoy ADMIN = todos).

## 6. Decisiones

**Tomadas (2026-10-08):**
| ID | Decisión | Dónde |
|---|---|---|
| D-1 | `professional_schedules`: semanal (planta) y por fecha (ocasionales), excepciones con `available=false` | `schema.dbml`, §4.3 |
| D-2 | Botón Reprogramar en el recordatorio; la respuesta siempre se guarda (`RESCHEDULED` en el CHECK) | `schema.dbml`, §4.11 |
| D-3 | Cloud API sin verificación de empresa (producción) + número de prueba (desarrollo). Número del bot exclusivo; derivar = enlace wa.me de recepción | §4.11, `WHATSAPP-SETUP.md` |
| D-4 | ~~Servidor = PC de recepción~~ — reemplazada por D-17 | — |
| D-5 | `record_number` = `HC-000001` provisional hasta confirmar si es la cédula | §4.2 |
| D-6 | `closed_at` = al completar; corrección por anulación + registro, cabecera auditada por Envers | §4.4 |
| D-7 | NO_SHOW manual en el MVP | §4.3 |
| D-8 | Anulan pagos ADMIN y SECRETARIA, con motivo | §4.8 |
| D-9 | Activa = PENDING/CONFIRMED futura; 1 por paciente y especialidad + tope por teléfono (código, configurable) | §4.11 |
| D-10 | Cancelar o reprogramar por bot hasta 2 h antes | §4.11 |
| D-11 | `postgres:16` también en `docker-compose` de despliegue | §4.12 |
| D-12 | La secretaria registra los horarios: permiso `SCHEDULE_MANAGE` para ADMIN y SECRETARIA | tarea 002, §4.3 |
| D-13 | Alta de cuentas para profesionales: posterior al MVP; endpoints estipulados; mientras tanto, por migración | §4.13 |
| D-14 | Odontograma completo: simbología en `dental_condition_symbols`, alcance SPAN para prótesis, categoría periodontal y rango de grado | `schema.dbml`, §4.5 |
| D-15 | appointments lee el vínculo cita→atención con EncounterRepository (solo lectura) para evitar el ciclo con clinical | §4.3 |
| D-16 | Reemplazar los ítems de una receta DRAFT borra sus filas de `prescription_items`; es la única excepción a «sin DELETE en datos clínicos», porque un borrador nunca se entregó al paciente. Los ítems de una receta ISSUED nunca se borran | §4.7 |
| D-17 | Servidor = VPS en la nube con Docker Compose y Caddy. VPS, dominio, Meta, proveedor de IA y Drive a nombre de la clínica al implantar (temporalmente los paga el desarrollador). Proveedores = encargados del tratamiento | §1, `CONTEXT.md §8` |
| D-18 | Respaldos: `pg_dump` diario en el VPS (30 días) + copia cifrada en Google Drive de la clínica. Solo dos copias; restauración documentada, sin prueba en el MVP | §1, §4.12 |
| D-19 | Bot híbrido: agente Python (WhatsApp, conversación, NLU) sin acceso a la BD + API interna Java `/api/v1/bot/*` con clave de servicio, que aplica las reglas y es la única que escribe; escrituras solo tras *Confirmar* | §4.11 |
| D-20 | NLU con un LLM por API, interfaz intercambiable; al LLM solo el texto del mensaje (sin nombre, cédula ni historial); proveedor sin entrenamiento con datos de la API; sin IA el bot cae al menú | §4.11 |
| D-21 | Dolor o urgencia: 3 primeros huecos de hoy o mañana en la especialidad de urgencias + texto fijo de emergencia; fuera de horario, además el teléfono de la doctora titular; sin consejo médico. Reemplaza "dolor → derivar" | §4.11 |
| D-22 | Número del bot = chip prepago dedicado (no un número personal ni el de recepción). Límites confirmados: 250 destinatarios/24 h por portafolio, máx. 2 números | §4.11, `WHATSAPP-SETUP.md` |
| D-23 | Conjunto de evaluación del NLU (precisión por intención, tareas completadas, derivaciones, costo): fuera del MVP, queda como recomendación de la tesis | `MVP-SCOPE.md` |

**Pendientes:**
| ID | Decisión | Propuesta | Afecta |
|---|---|---|---|
| D-5b | Confirmar con la clínica si el N° de HC es la cédula | — | 005 |
| D-14b | Odontóloga: ¿CROWN cuenta como O en CPO? ¿Corona/prótesis por hacer = OTHER rojo? | CROWN = O/o; OTHER rojo | 009 |
| D-14c | Odontóloga: ¿caries tratada = obturación (FILLING) y extracción indicada realizada = perdido por caries (MISSING_CARIES)? | sí (CARIES→FILLING, EXTRACTION→MISSING_CARIES) | 010b |
| D-20b | Proveedor y modelo de IA para el NLU (se evaluarán opciones baratas, incluidos modelos chinos alojados fuera de China) | Decidir en 016e | 016e |
| D-21b | ¿Avisar a recepción cuando el bot agenda una urgencia? Hoy no hay bandeja: la cita aparece en la agenda con `source=BOT` | No avisar en el MVP | 016a |
