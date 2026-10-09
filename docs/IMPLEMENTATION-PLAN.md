# Plan de implementación — Bellidental MVP

Cómo se construye cada módulo de `MVP-SCOPE.md`. Cada §4.x es la base de una tarea `docs/tasks/NNN-*.md`:
la tarea copia de aquí objetivo, reglas y tests, y añade los archivos concretos.
Contexto de negocio: `CONTEXT.md`. Datos: `schema.dbml`.

Marcas: **[D-n]** = decisión pendiente (ver §6). Nada marcado así se implementa hasta aprobarse.

---

## 1. Arquitectura y despliegue (RNF04, RNF05)

**Topología local (D-4: el servidor es el PC de recepción; no hay presupuesto para un equipo dedicado):**
```
PC de recepción (también es el servidor)
 └─ Docker Compose
     ├─ postgres:16          (volumen local; único lugar donde viven los datos)
     ├─ backend (Spring Boot 3.4, Java 21)
     ├─ frontend (Angular, servido por nginx)  → accesible por LAN: http://bellidental.local
     ├─ backup  (pg_dump programado)
     └─ tunnel  (Cloudflare Tunnel: expone SOLO /api/v1/whatsapp/webhook a internet)
PCs de recepción y consultorios → navegador en la LAN
```
- Sin internet: todo funciona salvo bot y Google Calendar (se ponen al día al volver la conexión).
- Riesgos asumidos por usar el PC de recepción y cómo se mitigan:
  - **Si se apaga, se cae todo** (incluido el bot, que debería atender de 07:00 a 08:00 y fuera de horario).
    Mitigación: dejarlo encendido, sin suspensión ni hibernación; encendido automático por BIOS a las
    06:30 y reinicio tras un corte de luz; Docker arranca con el sistema (`restart: unless-stopped`).
    Si igual se apaga de noche, los mensajes del bot que lleguen se pierden, los recordatorios salen al
    encender y Google se reconcilia al arrancar.
  - **Recursos**: mínimo 8 GB de RAM; límites de memoria en Compose (JVM `-Xmx512m`, Postgres ~256 MB)
    para que la recepción siga usando el equipo.
  - **Un solo disco**: los respaldos fuera de ese disco son obligatorios, no opcionales.
  - **Uso compartido**: cuenta de sistema operativo de la secretaria sin permisos de administrador;
    disco cifrado (BitLocker o LUKS) si el sistema operativo lo permite.
- El túnel no abre puertos en el router; expone únicamente el webhook de WhatsApp.
- Sin licencia ni verificación remota. Se entregan `docker-compose.yml`, `.env.example` y guía de
  instalación/restauración.
- Respaldos: `pg_dump -Fc` diario 20:30 a disco local, retención 30 días; copia **diaria** a un USB o
  disco externo y semanal a la nube de la propia clínica (Google Drive del director), cifrada. Procedimiento de restauración probado y documentado.
- Exportación abierta: ZIP con un CSV por tabla (UTF-8, cabecera) + dump SQL, solo `ADMIN`.
- Contingencia (BN10): si el sistema cae, se usa la ficha rápida en papel y se carga al volver.

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

| Tarea | Módulo | Migración | Depende de | `*IT` |
|---|---|---|---|---|
| 001 | Esquema base identity/staff/patients | V1–V3 | — | `SchemaMigrationIT` (en correcciones) |
| 002 | Semillas base | V4 | 001 | `BaseSeedIT` |
| 003 | Auth email + JWT | — | 002 | `AuthControllerIT` |
| 004 | Pacientes y profesionales (API) | — | 003 | — |
| 005 | Historia clínica + antecedentes | V5 | 004 | — (cubierto en 008) |
| 006 | Agenda + horarios | V6 (incluye `professional_schedules`) | 004 | `AppointmentOverlapIT` |
| 007 | Catálogos clínicos: CIE-10, pg_trgm | V7 parte catálogo | 005 | — |
| 008 | Atención clínica (encounters + diagnósticos) | V7 | 006, 007 | `EncounterFlowIT` |
| 009 | Odontograma | V8 | 008 | — |
| 010 | Tratamientos | V9 | 008, 009 | — |
| 011 | Recetas + PDF | V10 | 008 | — |
| 012 | Cobros + comprobante | V11 | 010 | — |
| 013 | Auditoría Envers | V12 | 012 | — (añade un caso a `EncounterFlowIT`) |
| 014 | Google Calendar (sistema → Google) | — | 006 | — |
| 015 | Bot: núcleo, aviso, info, derivar | V13 | 006 | — |
| 016 | Bot: agendar y reprogramar | — | 015 | — |
| 017 | Bot: recordatorios | — | 016 | — |
| 018 | Respaldos y exportación | — | todo | — |

(007 y 008 pueden ir en una sola tarea si V7 se mantiene como una migración: CIE-10 + encounters.)

---

## 4. Módulos

### 4.1 Pacientes y profesionales — tarea 004 (RF06 parcial, RF07 base)
Los módulos ya existen; se ajustan y se exponen solo estas operaciones.

**Pacientes** (`com.bellidental.patients`)
- `POST /patients` (PATIENT_CREATE): cédula, nombres, fecha de nacimiento, sexo obligatorios; teléfono
  normalizado a E.164 sin "+" (`0987654321` → `593987654321`). Cédula ecuatoriana validada (dígito
  verificador) cuando `document_type = CI`.
- `PUT /patients/{id}` (PATIENT_UPDATE). `admin_notes` editable por recepción.
- `GET /patients?query=` (PATIENT_READ): por cédula exacta, prefijo de teléfono o nombre (ILIKE ahora;
  trigram cuando exista el índice de V7).
- `GET /patients/{id}`: identificación + edad calculada. Nunca datos clínicos.
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
- Endpoints: `GET /professionals/{id}/schedules?from&to` (horario efectivo por día),
  `POST/PUT /professionals/{id}/schedules`, `POST /schedules/{id}/deactivate`.
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
- Disponibilidad: `GET /availability?professionalId&specialtyId&date` → huecos libres (horario − citas
  activas), en pasos de la duración de la especialidad. La usan recepción y bot.

Endpoints: `GET /appointments?professionalId&from&to` (vista calendario con color),
`POST /appointments`, `POST /{id}/confirm|cancel|check-in|no-show|reschedule`, `POST /walk-in`,
`GET /availability`.

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

**Atención:**
- `POST /appointments/{id}/encounter` (ODONTOLOGO): solo si la cita tiene check-in y no tiene atención;
  `professional_id` = profesional del usuario actual (quien atiende, puede diferir del agendado);
  abre la historia clínica si no existe.
- `PUT /encounters/{id}`: secciones 1, 2, 4, 5, 7, 10 del 033. Solo mientras `status=OPEN`.
- `POST /encounters/{id}/diagnoses` (icd10, PRESUMPTIVE/DEFINITIVE, notas); duplicado activo → 409.
- `POST /encounters/{id}/complete`: exige `chief_complaint` y ≥1 diagnóstico; `status=COMPLETED`,
  `end_at=now`; cita → `ATTENDED`.
- **Cierre (D-6)**: al completar se fijan `status=COMPLETED` y `closed_at=now` a la vez. Desde ahí no
  se edita nada de la atención en sitio. Correcciones:
  - Ítems (diagnósticos, hallazgos, tratamientos, recetas): anular el incorrecto con motivo
    (`voided_at`, `void_reason` donde exista) y registrar el correcto **en la misma atención**
    (se permite añadir ítems a una atención cerrada, solo para corregir, solo el odontólogo que atendió).
  - Campos de cabecera (motivo, signos, examen, planes): no tienen anulación por campo. Se permite
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

Unit: reglas de apertura/completado, detección código vs texto, numeración de sesiones.
IT `EncounterFlowIT`: check-in → abrir → diagnóstico → completar → cita ATTENDED;
la secretaria recibe 403 al abrir atención.

### 4.5 Odontograma — tarea 009 (RF02)
Paquete `com.bellidental.odontogram`. Migración V8: `teeth` (52 piezas FDI), `tooth_surfaces`
(O, V, L, M, D), `dental_conditions` con semilla de las filas del 033:

| code | scope | uses_grade | cpo | ceo |
|---|---|---|---|---|
| MOBILITY | TOOTH | sí (1–3) | — | — |
| RECESSION | TOOTH | sí (1–3) | — | — |
| CARIES | SURFACE | — | C | c |
| FILLING (obturación/calza) | SURFACE | — | O | o |
| MISSING (pieza perdida) | TOOTH | — | P | — |
| EXTRACTION (por extraer) | TOOTH | — | P (si PENDING) | e |
| ROOT_CANAL (endodoncia) | TOOTH | — | — | — |
| SEALANT (sellante) | SURFACE | — | — | — |

(Revisar la tabla con la odontóloga; caries simple/compuesta/compleja = 1, 2, 3+ caras con CARIES,
calculado, no condiciones distintas.)

Reglas:
- Solo ODONTOLOGO, en atención OPEN. Condición SURFACE exige cara; TOOTH la prohíbe. `grade` solo si
  `uses_grade`. Piezas temporales (51–85) permitidas.
- `status`: PENDING (rojo) / DONE (azul).
- **Estado vigente (O3)**: último hallazgo no anulado por (pieza, cara) en SURFACE y por (pieza,
  condición) en TOOTH, entre todas las atenciones no anuladas del paciente.
- CPO/ceo calculado del estado vigente: C=cariados, P=perdidos o extracción indicada, O=obturados;
  cada pieza cuenta una vez por componente.

Endpoints: `GET /patients/{id}/odontogram` (vigente + CPO/ceo), `GET /encounters/{id}/findings`,
`POST /encounters/{id}/findings`, `POST /findings/{id}/void`.
Unit: estado vigente, CPO/ceo, validaciones de cara/grado.

### 4.6 Tratamientos — tarea 010 (RF04)
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
- Interfaz pública `ChargeLedger` para cobros: cargos por paciente (fecha, tratamiento, profesional,
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
Paquete `com.bellidental.billing`. Migración V11: `payments`, secuencia de comprobante
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
  patients. V12 crea `revision_info` y las tablas `*_aud`.
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

### 4.11 Bot WhatsApp — tareas 015–017 (RF10–RF15, RNF03)
Paquete `com.bellidental.whatsapp`. WhatsApp Cloud API (Meta): webhook `POST /api/v1/whatsapp/webhook`
(verificación con `hub.verify_token` y firma `X-Hub-Signature-256`), envío por Graph API.

**Cuenta de WhatsApp (D-3, no negociable)** — por falta de tiempo para el trámite de verificación de
empresa ante Meta:
| Entorno | Modalidad | Uso |
|---|---|---|
| Desarrollo | Número de prueba de Meta (sandbox): gratis, solo a destinatarios registrados a mano (máx. 5) | Desarrollo y demos |
| Producción | Número real conectado a Cloud API **sin verificación de empresa** (nivel básico, sin verificar) | Pacientes reales |

- Sin verificación, los mensajes que **inicia la clínica** (plantillas: recordatorios) tienen un tope de
  destinatarios únicos por 24 h (≈250 en la documentación de Meta; confirmarlo al configurar). Con
  ~26 citas/día los recordatorios caben. Las respuestas a pacientes que escriben primero (ventana de
  24 h) no cuentan para ese tope.
- Requisitos: app en Meta for Developers, cuenta de WhatsApp Business (WABA), un número **que no esté
  registrado en la app de WhatsApp** (o que se dé de baja de ella), nombre visible, plantilla del
  recordatorio aprobada (categoría *utility*), token permanente de usuario de sistema y, si Meta cobra
  las plantillas, un método de pago. Revisar costos vigentes por mensaje en Ecuador.
- Consecuencia: **el número del bot es exclusivo del bot**. La recepción sigue con su número actual en
  WhatsApp Business app.
- El código no cambia entre entornos: número, token e id de plantilla van por configuración
  (`application.whatsapp.*`). Verificar la empresa más adelante solo sube los topes.
El bot actúa como `bot_whatsapp`; solo usa `PatientDirectory`, `AppointmentScheduler`,
`ProfessionalDirectory`, catálogo de precios y `application.clinic.*`. **Nunca** módulos clínicos ni
cobros (RNF03): saldos y preguntas clínicas se derivan.

**Estado de conversación (O7):** en memoria (Caffeine) por teléfono, TTL 30 min: paso actual,
paciente identificado, selección parcial, `handoffUntil`.

**015 — Núcleo**
- Aviso de privacidad (RF10): si no hay `privacy_consents` para el teléfono con la versión vigente,
  primero se envía el aviso con botones *Acepto / No acepto*. Sin aceptar no se procesa nada más.
  Al aceptar se guarda (con `patient_id` si se identifica).
- Menú (lista interactiva): Horarios · Precios y formas de pago · Agendar · Reprogramar ·
  Hablar con recepción.
- Info (RF11): horarios y formas de pago de configuración; precios referenciales de
  `treatment_catalog` activos (los marcados como públicos o una lista en configuración).
- Derivar (RF14, D-3): texto libre no reconocido, palabras clínicas (dolor, sangrado, receta…), saldo,
  paciente no registrado o "hablar con recepción" → mensaje con el horario y el enlace
  `https://wa.me/<número de recepción>` (configuración) para escribir a la secretaria; `handoffUntil`
  = +4 h (el bot no responde a ese número salvo que elija una opción del menú). Sin bandeja en el
  sistema ni tablas nuevas.
- Identificación: pacientes activos con ese `phone_e164`. 0 → derivar (paciente nuevo).
  1 → "¿La cita es para Juan P.?" Sí/No. Varios → pedir cédula y validar que pertenezca al grupo.

**016 — Agendar (RF12) y reprogramar (RF15)**
- Agendar: especialidad → profesional (o "el primero disponible") → día (próximos 14 días con huecos)
  → hora (máx. 10 opciones) → confirmar. Crea cita `source=BOT`, `PENDING`. Si al confirmar el hueco
  ya se ocupó (409), se avisa y se ofrecen otros.
- Límites del bot (D-9), configurables en `application.whatsapp.limits.*` y validados en el código:
  - Cita **activa** = `status IN (PENDING, CONFIRMED)` y `start_at > now`. No cuentan las CANCELLED,
    ATTENDED, NO_SHOW ni RESCHEDULED (la cita que reemplaza a una reprogramada sí cuenta).
  - Máx. 1 cita activa creada por el bot por paciente y especialidad (limpieza y ortodoncia pueden
    convivir).
  - Máx. N citas activas creadas por el bot por teléfono, sumando todos los pacientes de ese número
    (por defecto 3), para que un número no acapare la agenda.
  - Al superar un límite: se explica y se deriva a recepción. Recepción no tiene estos límites.
- Reprogramar: lista las citas futuras PENDING/CONFIRMED del paciente → elige → huecos del mismo
  profesional y especialidad → confirma → usa la operación de reprogramar de la agenda (original
  `RESCHEDULED`, nueva con `rescheduled_from_id`, `source=BOT`). No se permite con menos de 2 h de
  anticipación (D-10): se deriva.
- Mensajes con fecha en español y hora local ("martes 14 de octubre, 10:30").

**017 — Recordatorios (RF13)**
- Al crearse una cita activa: `appointment_reminders` `DAY_BEFORE`, `scheduled_at` = día anterior 09:00
  local. Si ese momento ya pasó, no se crea (el del mismo día está fuera).
- Al cancelar/reprogramar/no-show: el recordatorio PENDING pasa a `CANCELLED`; la cita nueva crea el suyo.
- Job cada 5 min: PENDING con `scheduled_at ≤ now` → envía plantilla aprobada por Meta (categoría
  *utility*) con botones *Confirmar / Cancelar / Reprogramar* [D-2] → `SENT` + `wa_message_id`; error →
  `FAILED` (1 reintento).
- Respuesta (por `wa_message_id`), siempre se guarda en el recordatorio (`RESPONDED`, `response`,
  `responded_at`) (D-2): Confirmar → `CONFIRMED` y cita `CONFIRMED`; Cancelar → `CANCELLED` y cita
  `CANCELLED` (libera el hueco); Reprogramar → `RESCHEDULED` y se abre el flujo de 016 (si el paciente lo
  abandona, la cita sigue como estaba).
- Cancelar o reprogramar por bot solo hasta **2 h antes** de la cita (D-10); después → se deriva.
Unit (todo el bot): máquina de pasos, identificación por teléfono, derivación, cálculo de
`scheduled_at`, manejo de respuesta, verificación de firma. Sin `*IT` (no es módulo crítico).

### 4.12 Respaldos y exportación — tarea 018 (RNF04)
- Compose de despliegue con `postgres:16` (D-11). Servicio `backup` (imagen postgres:16 + cron) con la política de §1; script de
  restauración `scripts/restore.sh`.
- `GET /admin/export` (ADMIN): ZIP con CSV por tabla (incluye `*_aud`), generado en streaming.
- Doc `docs/OPERATIONS.md`: instalar, respaldar, restaurar, exportar, rotar el secreto JWT.
Unit: escritor CSV (escapes, UTF-8, cabeceras).

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
| D-3 | Cloud API sin verificación de empresa (producción) + número de prueba (desarrollo). Número del bot exclusivo; derivar = enlace wa.me de recepción | §4.11 |
| D-4 | Servidor = PC de recepción, con las mitigaciones de §1 | §1 |
| D-5 | `record_number` = `HC-000001` provisional hasta confirmar si es la cédula | §4.2 |
| D-6 | `closed_at` = al completar; corrección por anulación + registro, cabecera auditada por Envers | §4.4 |
| D-7 | NO_SHOW manual en el MVP | §4.3 |
| D-8 | Anulan pagos ADMIN y SECRETARIA, con motivo | §4.8 |
| D-9 | Activa = PENDING/CONFIRMED futura; 1 por paciente y especialidad + tope por teléfono (código, configurable) | §4.11 |
| D-10 | Cancelar o reprogramar por bot hasta 2 h antes | §4.11 |
| D-11 | `postgres:16` también en `docker-compose` de despliegue | §4.12 |
| D-12 | La secretaria registra los horarios: permiso `SCHEDULE_MANAGE` para ADMIN y SECRETARIA | tarea 002, §4.3 |

**Pendientes:**
| ID | Decisión | Propuesta | Afecta |
|---|---|---|---|
| D-5b | Confirmar con la clínica si el N° de HC es la cédula | — | 005 |
