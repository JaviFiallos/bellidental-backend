# 006c — Horarios: respuestas con ApiResponse (G-8 del frontend)

> Corrección pequeña sobre la 006a. **La ejecutó Claude** a pedido del usuario (excepción al ciclo con Gemini).

## Objetivo
`ScheduleController` era el único controlador que devolvía los DTO sin el envoltorio estándar
`ApiResponse<T> { success, message, data }` (plan §2). El frontend (`ApiClient`) desenvuelve `data` en todas las
respuestas, así que no podía leer el horario efectivo que necesita la agenda de recepción (frontend 003a) ni los
horarios (frontend 006). Hay que envolver todos sus endpoints, sin cambiar rutas, permisos ni lógica.

## Archivos a tocar
```
appointments/
  api/ScheduleController.java      los 5 endpoints devuelven ResponseEntity<ApiResponse<…>>
src/test/java/com/bellidental/appointments/
  ScheduleApiIT.java               (nuevo)
```

## Criterios de aceptación
1. `GET /api/v1/professionals/{id}/schedules` → `200` `ApiResponse<List<ScheduleResponse>>`.
2. `GET /api/v1/professionals/{id}/schedules/effective?from&to` → `200` `ApiResponse<List<EffectiveDayResponse>>`.
3. `POST /api/v1/professionals/{id}/schedules` → `201` `ApiResponse<ScheduleResponse>`, message "Horario registrado.".
4. `PUT /api/v1/schedules/{id}` → `200` `ApiResponse<ScheduleResponse>`, message "Horario actualizado.".
5. `POST /api/v1/schedules/{id}/deactivate` → `200` `ApiResponse<Void>`, message "Horario desactivado.".
6. Mismos permisos (`PROFESSIONAL_READ`, `SCHEDULE_MANAGE`) y misma lógica de `ScheduleService`. Los errores siguen
   pasando por el manejador global.

## Tests requeridos
- Unit: ninguno (no hay reglas nuevas).
- Integration: `ScheduleApiIT`, 2 casos:
  1. crear horario de un día → `201` con el envoltorio y `data.id` → `GET …/effective` de ese día → `data[0].date` y
     el turno 08:00–12:00;
  2. actualizar → "Horario actualizado." con `data.startTime` 09:00 → desactivar → "Horario desactivado.".
- `AvailabilityIT` y `AppointmentOverlapIT` crean horarios por POST y solo miran el `201`: deben seguir pasando.

## Resultado
- `ScheduleController.java`: los 5 endpoints devuelven `ResponseEntity<ApiResponse<…>>`; el `201` del POST pasa de
  `@ResponseStatus` a `ResponseEntity.status(HttpStatus.CREATED)`, como en `ProfessionalController`; mensajes en
  español en create, update y deactivate. Sin cambios en el servicio ni en los DTO.
- `ScheduleApiIT.java` (nuevo): los 2 casos requeridos.
- `./mvnw -q verify -Dit.test='ScheduleApiIT,AvailabilityIT,AppointmentOverlapIT'`: 2 + 3 + 2 casos OK.
- `./mvnw -q clean verify`: OK, **109 tests, 0 fallos, 0 errores, 0 omitidos** (unit + IT).

## Revisión
**Estado: APROBADO** (ejecución y revisión de Claude a pedido del usuario). Contrato confirmado para el frontend:
`EffectiveDayResponse { date: "YYYY-MM-DD", shifts: [{ startTime, endTime }] }` dentro de `data`.
