# 031 — Bot: solo especialidades con profesionales activos

## Objetivo
Visto en producción con el bot de WhatsApp: el paciente eligió **Endodoncia**, que no tenía profesionales, y el bot
lo derivó a recepción sin poder agendar. `GET /api/v1/bot/specialties` devuelve todas las especialidades activas,
aunque nadie las atienda. El bot solo debe ofrecer las que tienen al menos un profesional activo.
(El agente ya maneja el caso con un mensaje, tarea 016h del agente; esto lo evita en origen.)

**Solo lectura del bot.** Sin migraciones, DTO nuevos ni cambios en otros endpoints. `SpecialtyController` (el CRUD
del director) sigue devolviendo todas.

## Archivos a tocar
```
src/main/java/com/bellidental/bot/application/
  BotAvailabilityService.java     getActiveSpecialties filtra por profesionales activos
src/test/java/com/bellidental/bot/
  BotAvailabilityIT.java          +1 caso
docs/IMPLEMENTATION-PLAN.md       fila 031 en la tabla de tareas
```

## Criterios de aceptación
1. `getActiveSpecialties()` devuelve solo las especialidades de `professionalDirectory.findActiveSpecialties()` para
   las que `professionalDirectory.findActiveBySpecialty(id)` no está vacío. Se usa el `ProfessionalDirectory`
   existente; no se añaden métodos ni consultas nuevas (son 3–5 especialidades, N+1 no importa).
2. El resto de `BotAvailabilityService` (profesionales, días, huecos, urgencias) no cambia.
3. `GET /api/v1/bot/specialties` con una especialidad activa sin profesionales → no aparece en la respuesta; las que
   tienen profesional activo siguen apareciendo con la misma forma (`specialtyId`, `name`, `durationMinutes`).
4. Plan: fila `| 031 | Bot: /bot/specialties solo con especialidades que tienen profesionales activos | — | 015a |
   BotAvailabilityIT (caso nuevo) |`.
5. `./mvnw -q clean verify` pasa.

## Tests requeridos
- **Unit:** ninguno.
- **Integration:** `BotAvailabilityIT`: «especialidad activa sin profesionales → no aparece en `/bot/specialties`».
  Crear la especialidad en el propio test (por el repositorio o el endpoint del director, como hagan los demás
  casos del archivo) y comprobar que la lista no la contiene y sí contiene una con profesional.

## Resultado

**Archivos modificados**
- `src/main/java/com/bellidental/bot/application/BotAvailabilityService.java`: `getActiveSpecialties()` añade
  `.filter(s -> !professionalDirectory.findActiveBySpecialty(s.id()).isEmpty())`. Nada más cambia.
- `src/test/java/com/bellidental/bot/BotAvailabilityIT.java`: caso nuevo `specialties_withoutActiveProfessional_omitsSpecialty`
  (crea una especialidad como director por `POST /api/v1/specialties`, comprueba que no aparece en `/api/v1/bot/specialties`
  y que sí aparece la especialidad 1 con profesional). Imports añadidos.
- `docs/IMPLEMENTATION-PLAN.md`: fila 031 tras la 030.

**Tests**
- `BotAvailabilityIT` en aislado: 4 tests, 0 fallos (exit 0).
- `./mvnw -q clean verify` (una vez):
  - surefire: Tests run: 103, Failures: 0, Errors: 0, Skipped: 0
  - failsafe: Tests run: 97, Failures: 1, Errors: 0, Skipped: 0
  - Fallo: `PrescriptionPdfIT.downloadPdf_issuedPrescription_returnsPdfWithConfidentialAndPatientDocument`
    (aserción sobre el texto extraído del PDF de receta). No está relacionado con el cambio (no toca prescriptions
    ni bot). No se ha comprobado contra la línea base; queda pendiente de revisar.
- `BotAvailabilityIT` pasa dentro del verify completo.

**Supuestos**
- El caso nuevo usa el login del director (`director@bellidental.local`), igual que `CatalogAdminIT`, porque crear
  especialidades requiere ROLE_ADMIN.

## Revisión
Estado: APROBADO

- `getActiveSpecialties()` filtra con `findActiveBySpecialty` del `ProfessionalDirectory` existente; el resto del
  servicio no cambia. El caso nuevo de `BotAvailabilityIT` cubre el criterio 3 y la fila 031 está en el plan.
- El fallo de `PrescriptionPdfIT` no venía de esta tarea: el commit de configuración cambió el teléfono de ejemplo
  por el real y la aserción del pie de la receta seguía esperando `+593 00 000 0000`. Corregida en ese mismo commit.
- `./mvnw -q clean verify` (Claude, una vez): surefire 103 run, 0 fallos; failsafe 97 run, 0 fallos.
