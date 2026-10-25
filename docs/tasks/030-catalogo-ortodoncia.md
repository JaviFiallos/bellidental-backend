# 030 — Catálogo de ortodoncia: Z46.4, especialidad y tratamientos (G-17 del frontend, D-32)

## Objetivo
La mayoría de atenciones de la clínica son **cambios de ligas** (control de ortodoncia). Hoy no se pueden completar
bien: falta el diagnóstico CIE-10 que corresponde y no hay tratamientos de ortodoncia en el catálogo.
Decisión del usuario (2026-10-25, **D-32**):
- La ortodoncia se cobra como **un monto total acordado** que se registra una vez («Ortodoncia (tratamiento
  completo)») y se paga con **abonos libres**. El saldo de la cuenta muestra cuánto falta.
- Cada control se registra como «Control de ortodoncia» a **$0**: queda en la historia y no suma deuda.
- El control lleva el diagnóstico **Z46.4 Prueba y ajuste de dispositivo ortodóntico**. Se mantiene la regla
  «completar exige ≥ 1 diagnóstico activo» (`EncounterPolicy.checkCanComplete`).
- Especialidad «Ortodoncia» con duración corta para que la agenda no pierda huecos.

**Solo datos (una migración).** Sin cambios de código, DTO, permisos ni reglas. Precio y duración se pueden editar
después desde el Catálogo del director (backend 021). El frontend (tarea 026) busca los tratamientos **por código**,
así que los códigos son fijos.

## Archivos a tocar
```
src/main/resources/db/migration/
  V22__orthodontics_catalog.sql            nuevo
src/test/java/com/bellidental/
  clinical/Icd10SearchIT.java              +1 caso
  treatments/TreatmentFlowIT.java          +1 caso
docs/IMPLEMENTATION-PLAN.md                fila 030 en la tabla de tareas + D-32
```

## Criterios de aceptación
1. `V22__orthodontics_catalog.sql`, idempotente con `ON CONFLICT ... DO NOTHING`: si el director ya creó algo con
   el mismo código o nombre desde el Catálogo, no falla.
   - `icd10_codes`: `('Z46.4', 'Prueba y ajuste de dispositivo ortodóntico',
     'Z46 Prueba y ajuste de otros dispositivos')`.
   - `specialties`: `('Ortodoncia', 15, true)` (`name` es UNIQUE). Ajustar la secuencia si hace falta, como en V4.
   - `treatment_catalog`, con `specialty_id` = la especialidad Ortodoncia (subconsulta por nombre):
     - `D8080` · `Ortodoncia (tratamiento completo)` · descripción `Monto total acordado; se paga con abonos` ·
       `1200.00`
     - `D8670` · `Control de ortodoncia (cambio de ligas)` · descripción `Control periódico; sin cargo (cubierto
       por el tratamiento completo)` · `0.00`
   - Comentario de cabecera que cite la tarea 030 y D-32.
2. `GET /api/v1/icd10?query=Z46` devuelve `Z46.4`.
3. `GET /api/v1/treatments?query=D86` devuelve `D8670` con `basePrice` 0.00. Registrarlo con `price: 0` en una
   atención responde 201 con `total` 0.00. `@DecimalMin("0.00")` ya lo permite: no se toca.
4. Una atención con Z46.4 y D8670 a $0 se completa (`POST /encounters/{id}/complete` → 200, `COMPLETED`).
5. `./mvnw -q clean verify` pasa: es una tarea con migración.
6. Plan: fila `| 030 | Catálogo de ortodoncia: Z46.4, especialidad Ortodoncia (15 min), D8080 y D8670 (G-17 del
   frontend, D-32) | V22 | 007, 010a, 021 | Icd10SearchIT + TreatmentFlowIT (casos nuevos) |` y D-32 en decisiones.

## Tests requeridos
- **Unit:** ninguno (no hay reglas nuevas).
- **Integration:**
  - `Icd10SearchIT`: «buscar Z46 → devuelve Z46.4».
  - `TreatmentFlowIT`: «control de ortodoncia: Z46.4 + D8670 a $0 → 201 con total 0 → completar → COMPLETED».

## Resultado
- **Qué se hizo**:
  - Se creó la migración [V22__orthodontics_catalog.sql](file:///home/javif/Documentos/UTA/Titulacion/bellidental-app/bellidental-backend/src/main/resources/db/migration/V22__orthodontics_catalog.sql) idempotente (`ON CONFLICT ... DO NOTHING`) con el diagnóstico CIE-10 `Z46.4` en `icd10_codes`, la especialidad `Ortodoncia` (15 min) en `specialties` (con ajuste de secuencia), y los tratamientos `D8080` ($1200.00) y `D8670` ($0.00) vinculados a la especialidad Ortodoncia en `treatment_catalog`.
  - Se añadió en [Icd10SearchIT](file:///home/javif/Documentos/UTA/Titulacion/bellidental-app/bellidental-backend/src/test/java/com/bellidental/clinical/Icd10SearchIT.java) el test `search_codePrefixZ46AsOdontologa_returnsZ464` que valida la búsqueda por prefijo `Z46` retornando `Z46.4`.
  - Se añadió en [TreatmentFlowIT](file:///home/javif/Documentos/UTA/Titulacion/bellidental-app/bellidental-backend/src/test/java/com/bellidental/treatments/TreatmentFlowIT.java) el test `recordTreatment_orthodonticsControlWithZeroPriceAndZ464_completesEncounter` que valida: búsqueda de `D86` retornando `D8670` con `basePrice = 0.00`, adición del diagnóstico `Z46.4`, registro del tratamiento a `$0` con respuesta 201 y total `0.00`, y completado exitoso de la atención (`POST /complete` -> 200, `COMPLETED`).
  - Se actualizó [docs/IMPLEMENTATION-PLAN.md](file:///home/javif/Documentos/UTA/Titulacion/bellidental-app/bellidental-backend/docs/IMPLEMENTATION-PLAN.md) con la fila de la tarea 030 en la tabla de tareas y la decisión D-32 en la tabla de decisiones arquitecturales.
- **Resultado de los tests**:
  - Surefire: 103 tests run, 0 failures, 0 errors, 0 skipped.
  - Failsafe: 96 tests run, 0 failures, 0 errors, 0 skipped.
  - `./mvnw -q clean verify` pasó exitosamente.
- **Problemas encontrados y supuestos tomados**:
  - Ninguno. Las validaciones existentes (`@DecimalMin("0.00")` y `EncounterPolicy.checkCanComplete`) permitieron directamente el registro de tratamientos a $0 y la completitud de la atención con diagnósticos Z46.4 sin modificar código de la aplicación.

## Revisión
Estado: APROBADO

- V22 cumple el criterio 1: `ON CONFLICT` sobre `icd10_codes.code`, `specialties.name` y `treatment_catalog.code`
  (todas UNIQUE), `setval` como en V4, `specialty_id` por subconsulta y cabecera con 030/D-32.
- Los tests nuevos cubren los criterios 2–4 (Z46 → Z46.4; D86 → D8670 a 0.00; 201 con total 0; completar → COMPLETED).
- Plan: fila 030 y D-32 añadidas.
- `./mvnw -q clean verify` corrido por Claude (tarea con migración): surefire 103/0/0/0, failsafe 96/0/0/0.
- Nota: el cambio en `.gitignore` (`proy-infor/`) ya estaba antes de esta tarea y no es parte de ella; no incluirlo
  en el commit de la 030.
