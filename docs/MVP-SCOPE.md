# Alcance del MVP — Bellidental

Origen: Capítulo III de la tesis. Objetivo medible: reducir el TGA
(T1 receta+registro, T2 pago, T3 agenda; T4+T5 transcripción desaparecen).
Contexto: `docs/CONTEXT.md`. Plan por módulo: `docs/IMPLEMENTATION-PLAN.md`.

## Módulos y migraciones (orden)
| # | Módulo `com.bellidental.*` | Migración | Requerimientos |
|---|---|---|---|
| 1 | identity | V1__identity | RNF01 |
| 2 | professionals + specialties | V2__staff | RF07 |
| 3 | patients | V3__patients | RF06 |
| 4 | semillas base | V4__seed_base_data | RNF01 |
| 5 | auth (login por email, JWT) | — | RNF01 |
| 6 | clinical_records + antecedentes | V5 | RF01 |
| 7 | appointments (agenda, cruces, check-in, reprogramar) | V6 | RF07, RF15 |
| 8 | clinical: encounters + CIE-10 | V7 | RF01, RF03 |
| 9 | odontogram | V8 | RF02 |
| 10 | treatments | V9 | RF04 |
| 11 | prescriptions + PDF | V10 | RF04, RF05 |
| 12 | billing | V15 | RF08 |
| 13 | auditoría (Envers + Spring Data Auditing) | V16 | RNF02 |
| 14 | Google Calendar (sistema → Google) | — | RF07 |
| 15 | bot WhatsApp: API interna (Java) + agente (Python) | V13 | RF10–RF15 |
| 16 | despliegue en VPS, respaldos y exportación | — | RNF04 |

## Requerimientos MVP
- RF01 Historia clínica con todos los campos del 033; antecedentes registrados por el odontólogo.
- RF02 Odontograma por pieza y cara; rojo pendiente, azul realizado; solo ODONTOLOGO edita.
- RF03 CIE-10 con búsqueda por prefijo y texto (pg_trgm).
- RF04 Catálogos de tratamientos y fármacos con posología predefinida.
- RF05 Receta desde catálogos, ligada a un diagnóstico de la atención; PDF con firma y "CONFIDENCIAL".
- RF06 Historial inmediato de cualquier paciente.
- RF07 Agenda única por profesional, colores, sin cruces (EXCLUDE en BD).
- RF08 Pagos con abono; saldo calculado, nunca almacenado; comprobante interno (no SRI).
- RF09 Vista e impresión del 033 con su numeración y distribución (entra al MVP el 2026-10-19). El backend da los
  datos (`clinical-summary`, tarea 011c); la hoja se arma e imprime en el frontend (HTML A4).
- RF10 Aviso de privacidad con aceptación. RF11 Horarios y precios.
- RF12 Agendar por WhatsApp (solo pacientes registrados). RF13 Recordatorio con botones Confirmar/Cancelar.
- RF14 Derivar a recepción las preguntas clínicas, saldos o lo no reconocido. Dolor o urgencia: se ofrece el primer
  hueco disponible y un texto fijo de emergencia, sin consejo médico (D-21).
- RF15 Reprogramar por WhatsApp (pacientes registrados), además de en recepción y desde el recordatorio.
- RF11–RF15 también por texto libre: el bot entiende lenguaje natural (intención y datos) además de menú y
  botones; la IA interpreta, el backend decide y escribe (D-19, D-20).
- RNF01 Roles ADMIN, ODONTOLOGO, SECRETARIA fijos; login por email; el ADMIN gestiona usuarios (D-24); sin UI de permisos.
- RNF02 Trazabilidad (Envers). RNF03 Bot sin datos clínicos.
- RNF04 Datos bajo control de la clínica: VPS contratado a nombre de la clínica (sin SaaS del desarrollador ni
  licencia), sin borrado físico, respaldos automáticos (VPS + Google Drive de la clínica), exportación completa
  en formato abierto (D-17, D-18).
- RNF05 Disponibilidad: el sistema vive en un VPS; sin internet en la clínica se usa la contingencia en papel
  (BN10) y el bot sigue atendiendo. RNF06 Registro rápido en consulta.

## Fuera del MVP (no implementar)
RF16 citas por terceros · RNF07 firma
electrónica · bot para pacientes nuevos · facturación SRI ·
recordatorio del mismo día · sincronización Google → sistema · pruebas de restauración de respaldos ·
conjunto de evaluación del NLU (queda como recomendación de la tesis, D-23).

## Decisiones (no cambiar sin aprobación)
- Toda atención tiene cita; paciente sin cita = cita `source=WALK_IN`.
- `encounters` sin `patient_id`; `professional_id` = quien atendió.
- Reprogramar (recepción o bot): original `RESCHEDULED` + cita nueva con `rescheduled_from_id`.
- Usuario técnico `bot_whatsapp` (`is_system=true`, no puede iniciar sesión).
- Datos clínicos separados de identificación; la secretaria no accede a tablas clínicas.
- Teléfonos E.164 sin "+" (`593987654321`), no únicos. Cédula y fecha de nacimiento obligatorias.
- Horario por profesional (`professional_schedules`): semanal para los de planta, por fecha para especialistas
  ocasionales (de pocas horas a todo el día); las filas por fecha reemplazan a las semanales de ese día.
- WhatsApp: Cloud API con número real sin verificación de empresa (producción, chip prepago dedicado) y número
  de prueba (desarrollo). El número del bot es exclusivo; derivar = enviar el enlace del WhatsApp de recepción.
  Pasos y tareas: `docs/WHATSAPP-SETUP.md`.
- Bot híbrido: agente Python (WhatsApp, conversación, NLU con LLM) sin acceso a la BD; toda lectura y escritura
  pasa por la API interna `/api/v1/bot/*` del backend, que aplica las reglas. Solo temas de la clínica
  (política de Meta sobre IA de propósito general).
- Bot: cita "activa" = `PENDING`/`CONFIRMED` futura; máx. 1 por paciente y especialidad y un tope por teléfono
  (configurable); cancelar o reprogramar por bot hasta 2 h antes.
- Atención completada = cerrada: `closed_at` se fija al completar; los errores se corrigen anulando y registrando.
- Servidor: VPS en la nube con Docker Compose (reemplaza al PC de recepción). VPS, dominio, Meta, proveedor de IA
  y Drive a nombre de la clínica al implantar; mientras tanto los paga el desarrollador.
- Google Calendar: un calendario por profesional, cuenta de servicio, solo sistema → Google
  (la agenda del sistema es la única fuente; Google es espejo de lectura).
