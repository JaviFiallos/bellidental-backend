# Contexto del dominio — Bellidental

Por qué existe cada requerimiento. Se lee una vez por sesión de planificación; no hace falta volver a
`proy-infor/` salvo para citar textualmente.

**Jerarquía de documentos (si hay contradicción, gana el de arriba):**
1. `docs/MVP-SCOPE.md` — contrato del alcance (qué entra y qué no).
2. `docs/schema.dbml` — diseño de datos.
3. `docs/IMPLEMENTATION-PLAN.md` — cómo se construye cada módulo (reglas, endpoints, tareas).
4. Este archivo — por qué (clínica, flujo, normativa, mapeos de formularios).
5. `proy-infor/` — fuentes crudas (entrevistas, capítulo III v. antigua, fichas).

---

## 1. La clínica

- Centro Odontológico Bellidental (Ecuador, zona `America/Guayaquil`, UTC−5 sin horario de verano).
- Horario: 08:00–12:00 y 14:00–20:00. Fuera de horario la doctora titular atiende el teléfono.
- Volumen: ~26 atenciones/día observadas (pretest n=78); picos L-M-V con 3 doctores (~45–50/día);
  martes endodoncista (60 min/paciente, 8–9/día) en paralelo con el director (30 min, ~20/día).
  >1000 pacientes históricos en Excel; ~75 pacientes/semana.
- WhatsApp: 20–30 solicitudes de cita/día al director; 26–50 mensajes/día sobre precios y horarios;
  20+ sin leer a las 07:00–08:00; 31–60 min/día solo confirmando citas; 3–5 inasistencias/semana.
- Hoy usan: ficha rápida (papel), Formulario 033 (papel), Excel (réplica del 033), Google Calendar
  compartido (un color por doctor) y WhatsApp manual.
- Preferencia explícita del director: que los datos no dependan de una suscripción que "deje de funcionar a
  los 6–7 meses" (perdió su información con un SaaS impago); disposición a pagar ~200 USD único. Se planteó
  instalación local y se cambió a un VPS a nombre de la clínica (D-17). Ver §8 y RNF04.

## 2. Actores → roles del sistema

| Actor real | Hoy hace | Rol en el sistema | Qué hará |
|---|---|---|---|
| Director / odontólogo principal | Atiende, coordina, agenda por su WhatsApp | `ADMIN` + `ODONTOLOGO` | Atención clínica completa; administración |
| Odontóloga de planta | Anamnesis, odontograma, procedimientos | `ODONTOLOGO` | Registra la atención en el sistema durante la consulta |
| Especialistas rotativos (endodoncia, ortodoncia) | Atienden días fijos | `ODONTOLOGO` (necesitan usuario) | Igual que la odontóloga |
| Secretaria (polifuncional) | Recepción, filiación, cobro, agenda, WhatsApp, transcripción | `SECRETARIA` | Filiación, agenda, check-in, cobros. **No** toca datos clínicos |
| Bot | — | usuario técnico `bot_whatsapp` (`is_system`) | Info, agendar, reprogramar, recordatorios, derivar |

Regla normativa (AM 5216-A arts. 14, 27, 38): recepción solo accede a identificación, agenda y cobros.
Hoy la secretaria registra el odontograma y transcribe la historia: eso es lo que el sistema elimina.

## 3. Los registros actuales

| Registro | Soporte | Quién / cuándo | Destino en el sistema |
|---|---|---|---|
| **Ficha rápida** (= "ficha pequeña", "hoja de abonos", "ficha manual": es una sola hoja) | Papel | Secretaria + doctor, **durante** la atención | Vista de paciente + estado de cuenta (§6) |
| Formulario 033 (MSP) | Papel | Secretaria, en diferido (tarde) | Historia clínica digital (§5) |
| Historial digital | Excel (réplica del 033, odontograma tabular por pieza) | Secretaria, en diferido | Desaparece |
| Agenda | Google Calendar | Secretaria y director, en paralelo | Agenda del sistema; Google queda como espejo |

Cada atención se registra **tres veces** y solo la primera ocurre con el paciente presente.

## 4. Flujo actual → flujo objetivo

### Etapa 1 (con el paciente) — actual
1. Recepción verifica la cita en Google Calendar.
2. Paciente nuevo: secretaria abre ficha rápida, toma filiación y **antecedentes en la sala de espera**
   (sin privacidad: el paciente omite o miente; PC5).
3. Entrega la ficha al doctor y le dicta verbalmente los antecedentes.
4. Doctor hace anamnesis (corrige antecedentes) y odontograma en papel (rojo=pendiente, azul=hecho;
   1 cuadro caries simple, 2 compleja). A menudo lo escribe la secretaria bajo dictado (PC3).
5. Tratamiento; receta manuscrita o dictada a la secretaria (ilegible; PC4).
6. Doctor dice verbalmente qué hizo; secretaria anota fecha, Dg, tratamiento, doctor en la ficha.
7. Cobro: abono y saldo en la misma tabla; paciente firma.
8. Próxima cita en Google Calendar.
9. Archiva la ficha para transcribir.

### Etapa 2 (diferida, ~190 min/día) — actual
Transcribe cada ficha al 033 en papel (T4) y al Excel (T5), interpretando trazos ajenos (PC2).
Prepara las fichas del día siguiente.

### Flujo objetivo (con el sistema)
1. **Recepción**: busca paciente (cédula/nombre/teléfono). Nuevo → solo filiación (sin antecedentes).
   Check-in sobre la cita; si no tiene cita → cita `WALK_IN`.
2. **Consultorio** (odontólogo, en su pantalla): abre la atención desde la cita; el sistema muestra
   historial inmediato (RF06). Registra antecedentes en privado (BN6), signos, examen, odontograma
   clicable (RF02), diagnósticos CIE-10 con búsqueda (RF03), tratamientos del catálogo con precio (RF04)
   y receta desde catálogo ligada a un diagnóstico, PDF listo para firmar (RF05). Cierra la atención.
3. **Recepción**: ve los tratamientos registrados y su valor (sin diagnósticos), cobra el abono, emite
   comprobante interno (RF08), agenda la siguiente cita sin cruces (RF07).
4. **No hay Etapa 2**: la historia ya está completa. Google Calendar se actualiza solo; el bot envía el
   recordatorio el día anterior.

## 5. Métrica de la tesis: TGA

`TGA = T_E1 + T_E2 = (T1 + T2 + T3) + (T4 + T5)` — tiempo administrativo por ciclo completo de atención
(excluye la atención clínica).

| Tramo | Qué mide | Pretest (min) | Lo ataca |
|---|---|---|---|
| T1 | Receta + registro del tratamiento | 3,40 | RF03, RF04, RF05 |
| T2 | Cobro y registro de abono/saldo | 1,60 | RF08 |
| T3 | Agendar la siguiente cita | 1,30 | RF07, RF12 |
| T4 | Transcripción al 033 | 3,94 | RF01, RF02 → desaparece |
| T5 | Digitalización a Excel | 3,38 | RF01, RF02 → desaparece |
| **TGA** | | **13,62** (≈354 min/día) | |

Implicación de diseño (RNF06): **cada pantalla de T1–T3 se mide en clics y segundos**. Valores por
defecto, autocompletar, posologías predefinidas, precio que viene del catálogo, duración de la cita que
viene de la especialidad. Cualquier paso extra en consulta empeora la métrica que defiende la tesis.

## 6. Puntos críticos y brechas normativas → respuesta del sistema

| ID | Problema | Respuesta |
|---|---|---|
| PC1 / BN1 / BN3 | Triple registro y transcripción diferida | Registro único durante la atención (RF01, RF02) |
| PC2 | Personal no clínico interpreta trazos | Odontograma estructurado (pieza/cara/condición) |
| PC3 / BN4 | Secretaria registra odontograma | Solo `ODONTOLOGO` escribe tablas clínicas |
| PC4 | Receta y CIE-10 manuscritos | Catálogos + búsqueda + PDF (RF03–RF05) |
| PC5 / BN6 | Antecedentes en sala de espera | Antecedentes solo en consultorio, por el odontólogo |
| PC6 | Historial no disponible (sin cita) | Historial inmediato de cualquier paciente (RF06) |
| PC7 | Agenda alimentada por canales paralelos | Agenda única con `EXCLUDE`; bot y Google leen de ella |
| PC8 | Secretaria sobrecargada | Bot absorbe info, agendamiento, confirmaciones |
| BN2 | 033 a mano | Impresión del 033 — **fuera del MVP** (RF09) |
| BN5 | Excel sin credenciales ni trazas | Usuarios individuales + Envers (RNF01, RNF02) |
| BN7 | Documentos sin rótulo | "CONFIDENCIAL" en todo PDF |
| BN8 | WhatsApp sin aviso de privacidad | Aviso con aceptación explícita (RF10) |
| BN9 | Mensajería sin controles | Bot sin datos clínicos (RNF03) |
| BN10 | Contingencia | Ante caída, registro manual (ficha rápida) y carga posterior |

Normas: AM 00115-2021 (HCU), AM 0009-2017 (HCE: integridad, credenciales únicas, imprimir todos los
campos), AM 5216-A (confidencialidad, separación identificación/clínico, rotulación), LOPDP (datos de
salud = categoría especial; art. 31 exime consentimiento para el registro clínico, no para el bot).

## 7. Comunicación con el paciente (bot)

| Necesidad | Aceptación | Intención |
|---|---|---|
| N1 Horarios, precios, formas de pago | 62,8 % / 61,9 % | I1, I2 → RF11 |
| N2 Agendar sobre disponibilidad única | 58,1 % | I3 → RF12 |
| N2 Reprogramar | (P5 instrumento 3) | **I6 → RF15, ahora dentro del MVP** |
| N3 Recordatorio confirmar/cancelar | 53,3 % | I4 → RF13 |
| N4 Coexistencia con recepción y terceros | 35,2 % no usaría bot | Agenda única; terceros fuera |
| N5 Consultas humanas | — | I5 → RF14 derivar |

El bot **complementa** a recepción, no la reemplaza. Solo pacientes ya registrados (filiación en
recepción). Nada clínico ni saldos (se deriva). Entiende texto libre ("tengo un fuerte dolor", "¿cuánto cuesta
una limpieza?") además del menú: la IA interpreta la intención y el sistema responde con datos propios. Ante
dolor o urgencia ofrece el primer hueco disponible y un texto fijo de emergencia, nunca consejo médico (D-21).

## 8. Propiedad de los datos y despliegue en VPS (RNF04)

Requisito de origen: el director perdió su información con un sistema en la nube por suscripción impaga. Se
planteó instalar el sistema en el PC de recepción, pero eso traía riesgos (equipo apagado, un solo disco, uso
compartido). Decisión (D-17): **VPS en la nube a nombre de la clínica**. Lo que protege los datos no es dónde está
el servidor, sino de quién son las cuentas:
- VPS, dominio, portafolio de Meta, proveedor de IA y Google Drive con el correo y el medio de pago de la clínica
  (durante el desarrollo los paga el desarrollador). El desarrollador queda como colaborador.
- Sin licencia, clave de activación ni dependencia del desarrollador. El `docker-compose` es portable a otro
  proveedor.
- Respaldos automáticos en el VPS y en el Google Drive de la clínica (D-18); exportación completa en formato
  abierto: la clínica puede irse con sus datos.
- Encargados del tratamiento (LOPDP): proveedor del VPS, Meta (Cloud API) y proveedor de IA procesan datos por
  cuenta de la clínica. Se aceptan sus términos de tratamiento, se nombran en el aviso de privacidad del bot y se
  justifica la transferencia internacional (servidores fuera de Ecuador). Al LLM solo va el texto del mensaje,
  nunca nombre, cédula ni historial.
- Sin internet en la clínica no se puede usar el sistema (el bot sigue atendiendo): contingencia BN10.
Detalle técnico en `IMPLEMENTATION-PLAN.md §1`.

---

## 9. Formulario 033 → esquema

| Sección del 033 | Dónde vive | Nota |
|---|---|---|
| Encabezado: establecimiento | Configuración (`application.clinic.*`) | No es tabla |
| Nombre, apellido, sexo | `patients` | |
| Edad | Calculada de `patients.birth_date` a la fecha de la atención | No se guarda |
| N° historia clínica | `clinical_records.record_number` | Ver observación O6 |
| 1 Motivo de consulta | `encounters.chief_complaint` | Texto "PACIENTE REFIERE …" |
| 2 Enfermedad actual | `encounters.current_illness` | |
| 3 Antecedentes (10 ítems + "otro") | `medical_history_conditions` (catálogo de 10) + `medical_history_entries` (`scope` PERSONAL/FAMILY, `relative`) | "No refiere" → `clinical_records.no_known_history`; ver O5 |
| Medicación actual (entrevista) | `clinical_records.current_medication` | |
| 4 Signos vitales | `encounters.bp_systolic/diastolic, heart_rate, temperature, respiratory_rate` | |
| 5 Examen estomatognático | `encounters.stomatognathic_exam` | Texto libre como en su ficha |
| 6 Odontograma | `teeth`, `tooth_surfaces`, `dental_conditions`, `dental_condition_symbols`, `dental_findings` | Rojo=`PENDING`, azul=`DONE` (símbolo por condición y estado); prótesis con alcance SPAN; movilidad (0–3) y recesión (I–IV) como `PERIODONTAL` con `grade` |
| 7 Indicadores (periodontal, maloclusión, fluorosis) | `encounters.periodontal_disease / malocclusion / fluorosis` | LEVE/MODERADA/SEVERA; ANGLE_I/II/III |
| 8 Índices CPO / ceo | Calculados de `dental_findings` vía `cpo_component` / `ceo_component` | No se guardan |
| 10 Planes (Dx, terapéutico, educacional) | `encounters.diagnostic_plan / therapeutic_plan / educational_plan` | Biometría/química/RX van como texto en `diagnostic_plan` |
| 11 Diagnóstico (N°, CIE, PRE/DEF) | `encounter_diagnoses` (`certainty`) + `icd10_codes` | |
| Fecha apertura, profesional, código | `clinical_records.opened_at`, `opened_by` → `professionals.license_number` | |
| 12 Tratamiento: sesión y fecha | `encounters` ordenados por `start_at` del paciente (N° de sesión calculado) | |
| 12 Diagnósticos y complicaciones | `encounter_diagnoses` (+ `notes`) | |
| 12 Procedimientos | `performed_treatments` → `treatment_catalog.code` | Usan códigos tipo ADA (D0150, D1330) |
| 12 Prescripciones | `prescriptions` + `prescription_items`; indicaciones ("no comer 1 h") en `general_instructions` | |

Conclusión: el esquema cubre todos los campos del 033 que usa la clínica. Observaciones en §11.

## 10. Ficha rápida → sistema

| Campo | Destino | Quién |
|---|---|---|
| Nombre, F. nacimiento, cédula, teléfono | `patients` | Secretaria |
| Edad | Calculada | — |
| Antecedentes | `medical_history_entries` | **Odontólogo** (ya no recepción) |
| Factura | `payments.receipt_number` (comprobante interno, no SRI) | Secretaria |
| Observaciones (notas, presupuestos) | `patients.admin_notes` | Secretaria |
| Dg. (digitalizado) | Desaparece | — |
| Odontograma | `dental_findings` | Odontólogo |
| Tabla: fecha, Dg, tratamiento, Dr. | `encounters`, `encounter_diagnoses`, `performed_treatments`, `encounters.professional_id` | Odontólogo |
| Tabla: abono | `payments` (opcionalmente ligado a `encounter_id`) | Secretaria |
| Tabla: saldo | Calculado: Σ tratamientos − Σ pagos (nunca almacenado) | — |
| Firma del paciente | Línea de firma en el comprobante impreso | — |

La ficha rápida es la "vista de trabajo diaria": el sistema la reemplaza con dos vistas del mismo dato:
- **Odontólogo**: ficha completa (con Dg y odontograma).
- **Secretaria**: estado de cuenta (fecha, tratamiento, doctor, valor, abono, saldo) **sin diagnósticos**.

## 11. Observaciones al esquema (no se modifica; decidir después)

El esquema es sólido. Lo siguiente falta o necesita una regla; ordenado por impacto.

| # | Observación | Impacto | Propuesta mínima |
|---|---|---|---|
| O1 | **No hay horario de trabajo por profesional.** El bot (agendar/reprogramar) y la recepción necesitan saber qué días y horas atiende cada uno (la endodoncista solo martes). | Bloquea RF12 y RF15 | **Aprobado (D-1)**: `professional_schedules` en V6, semanal y por fecha |
| O2 | **Reprogramar desde el recordatorio**: `appointment_reminders.response` solo admite CONFIRMED/CANCELLED. | RF13 + RF15 | **Aprobado (D-2)**: ya en `schema.dbml` |
| O3 | **Estado actual del odontograma**: los hallazgos son por atención; no hay regla para saber si una caries roja ya se resolvió. | RF02, RF06 | Regla en código: estado vigente = último hallazgo no anulado por (pieza, cara) en SURFACE, (pieza, condición) en TOOTH y (pieza, pieza final, condición) en SPAN; MISSING_* oculta lo demás de la pieza (D-14) |
| O4 | **Walk-in vs EXCLUDE**: una cita `WALK_IN` que se cruce con otra del mismo profesional será rechazada por la BD. | Recepción | Walk-in exige hueco libre; si no hay, se asigna a otro profesional o se espera. Sin cambio de esquema |
| O5 | `no_known_history` es un solo booleano; el 033 distingue "no refiere personales" de "no refiere familiares". | Impresión 033 (fuera) | Derivable: sin entradas PERSONAL activas → "no refiere personales". Sin cambio |
| O6 | En Ecuador el N° de HCU suele ser la cédula. | Formato | Provisional `HC-000001` (D-5); confirmar con la clínica |
| O7 | Estado de la conversación del bot (paso del flujo, derivación a humano) no tiene tabla. | RF12, RF14, RF15 | En memoria del agente Python con TTL (una sola instancia). Sin cambio |
| O8 | Fallos de sincronización con Google no tienen columna de estado. | RF07 sync | Reconciliación diaria idempotente de citas futuras. Sin cambio |
| O9 | `pg_trgm` sobre nombre de paciente está en las notas pero no en V3. | RF06 búsqueda | Crear extensión e índice en V7 (junto a CIE-10) |
| O10 | Presupuestos (reverso de la ficha rápida) no están modelados. | No es requerimiento | Fuera; `admin_notes` lo cubre |
| O11 | Radiografías no están modeladas. | No es requerimiento | Fuera del MVP |
| O12 | `docker-compose.yml` usa `postgres:18`; los tests `postgres:16-alpine`. | Diferencias sutiles | **Aprobado (D-11)**: 16 en ambos |

## 12. Glosario

- **Atención / encounter**: lo que ocurre en el consultorio; siempre cuelga de una cita.
- **Cita WALK_IN**: cita creada en el momento para un paciente sin cita.
- **Hallazgo / dental_finding**: condición en una pieza (o cara) registrada en una atención.
- **Rojo / azul**: `PENDING` / `DONE`.
- **CPO / ceo**: cariados-perdidos-obturados (permanentes) / cariados-extracción indicada-obturados (temporales).
- **Saldo**: Σ (precio × cantidad) de tratamientos no anulados − Σ pagos no anulados del paciente.
- **Derivar**: el bot deja de responder y da el enlace del WhatsApp de recepción.
- **Agente**: servicio Python del bot (WhatsApp, conversación, NLU); no toca la BD, usa la API interna `/api/v1/bot/*`.
- **NLU**: comprensión del lenguaje natural; aquí, un LLM que devuelve intención y datos del mensaje.
- **FDI**: numeración de piezas 11–48 (permanentes) y 51–85 (temporales).
