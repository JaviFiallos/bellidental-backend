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
| 12 | billing | V11 | RF08 |
| 13 | auditoría (Envers + Spring Data Auditing) | V12 | RNF02 |
| 14 | Google Calendar (sistema → Google) | — | RF07 |
| 15 | bot WhatsApp | V13 | RF10–RF15 |
| 16 | despliegue local, respaldos y exportación | — | RNF04 |

## Requerimientos MVP
- RF01 Historia clínica con todos los campos del 033; antecedentes registrados por el odontólogo.
- RF02 Odontograma por pieza y cara; rojo pendiente, azul realizado; solo ODONTOLOGO edita.
- RF03 CIE-10 con búsqueda por prefijo y texto (pg_trgm).
- RF04 Catálogos de tratamientos y fármacos con posología predefinida.
- RF05 Receta desde catálogos, ligada a un diagnóstico de la atención; PDF con firma y "CONFIDENCIAL".
- RF06 Historial inmediato de cualquier paciente.
- RF07 Agenda única por profesional, colores, sin cruces (EXCLUDE en BD).
- RF08 Pagos con abono; saldo calculado, nunca almacenado; comprobante interno (no SRI).
- RF10 Aviso de privacidad con aceptación. RF11 Horarios y precios.
- RF12 Agendar por WhatsApp (solo pacientes registrados). RF13 Recordatorio con botones Confirmar/Cancelar.
- RF14 Derivar a recepción lo clínico, saldos o no reconocido.
- RF15 Reprogramar por WhatsApp (pacientes registrados), además de en recepción y desde el recordatorio.
- RNF01 Roles ADMIN, ODONTOLOGO, SECRETARIA fijos; login por email; sin UI de usuarios ni permisos.
- RNF02 Trazabilidad (Envers). RNF03 Bot sin datos clínicos.
- RNF04 Datos bajo control de la clínica: instalación local (sin SaaS, licencia ni dependencia remota),
  sin borrado físico, respaldos automáticos, exportación completa en formato abierto.
- RNF05/RNF04 Sin internet funciona todo salvo bot y Google Calendar. RNF05 Disponibilidad. RNF06 Registro rápido en consulta.

## Fuera del MVP (no implementar)
RF09 impresión del 033 · RF16 citas por terceros · RNF07 firma
electrónica · bot para pacientes nuevos · facturación SRI · UI de usuarios/permisos ·
recordatorio del mismo día · sincronización Google → sistema.

Los módulos existentes de creación de usuarios se conservan, pero no se exponen ni se amplían.

## Decisiones (no cambiar sin aprobación)
- Toda atención tiene cita; paciente sin cita = cita `source=WALK_IN`.
- `encounters` sin `patient_id`; `professional_id` = quien atendió.
- Reprogramar (recepción o bot): original `RESCHEDULED` + cita nueva con `rescheduled_from_id`.
- Usuario técnico `bot_whatsapp` (`is_system=true`, no puede iniciar sesión).
- Datos clínicos separados de identificación; la secretaria no accede a tablas clínicas.
- Teléfonos E.164 sin "+" (`593987654321`), no únicos. Cédula y fecha de nacimiento obligatorias.
- Horario por profesional (`professional_schedules`): semanal para los de planta, por fecha para especialistas
  ocasionales (de pocas horas a todo el día); las filas por fecha reemplazan a las semanales de ese día.
- WhatsApp: Cloud API con número real sin verificación de empresa (producción) y número de prueba (desarrollo).
  El número del bot es exclusivo; derivar = enviar el enlace del WhatsApp de recepción.
- Bot: cita "activa" = `PENDING`/`CONFIRMED` futura; máx. 1 por paciente y especialidad y un tope por teléfono
  (configurable); cancelar o reprogramar por bot hasta 2 h antes.
- Atención completada = cerrada: `closed_at` se fija al completar; los errores se corrigen anulando y registrando.
- Servidor: PC de recepción (sin equipo dedicado).
- Google Calendar: un calendario por profesional, cuenta de servicio, solo sistema → Google
  (la agenda del sistema es la única fuente; Google es espejo de lectura).
