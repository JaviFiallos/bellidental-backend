# 028 — Catálogo de medicamentos y posologías administrable (RF04, D-30)

## Objetivo
**Pedido del doctor**: hoy los medicamentos y sus posologías predefinidas (presets) solo se cargan por migración
(V10 + V19, 34 activos y 40 presets). El odontólogo debe poder **agregar, editar y desactivar** medicamentos y sus
posologías (sus "plantillas") desde la app. El catálogo es **compartido**: lo que crea un odontólogo lo ven todos
(sin `professional_id`; quién lo creó o cambió queda en Envers, como en la tarea 021). Decisión **D-30** (plan §6).

Reglas de fondo:
- Nunca `DELETE`: se desactiva (`active = false`). `prescription_items` apunta a `medications` y a `medication_presets`.
- **Medicamento ya usado en una receta → no se edita** (409). El PDF y la respuesta de la receta leen nombre,
  presentación y concentración del catálogo: editarlo cambiaría recetas ya emitidas. Se desactiva y se crea otro.
- Editar un preset sí se permite siempre: el ítem de receta guarda su propia copia de dosis/frecuencia/duración.
- `label`, `dose`, `frequency`, `duration` e `instructions` siguen siendo **texto libre** (solo `trim`, no vacío y
  longitud de columna). No se estructura la posología.
- Permiso `RECORD_CREATE` en todo lo nuevo (ODONTOLOGO y ADMIN; sin permiso nuevo, no se toca la semilla).
  `GET /medications?query=` no cambia de permiso.

## Archivos a tocar
```
src/main/resources/db/migration/V20__medication_catalog_admin.sql   (nuevo)
src/main/java/com/bellidental/
  persistence/entity/Medication.java                    (modif.) @Audited a nivel de clase
  persistence/entity/MedicationPreset.java              (modif.) + boolean active (columna active), @Audited
  persistence/repository/MedicationRepository.java      (modif.) + findAllByOrderByNameAscConcentrationAsc();
                                                        + existe por name/concentration/presentation ignorando mayúsculas
                                                        (variante "AndIdNot" para el PUT)
  persistence/repository/MedicationPresetRepository.java (modif.) + findByMedicationIdInAndActiveTrueOrderByIdAsc;
                                                        + existsByMedicationIdAndLabelIgnoreCase(AndIdNot)
  persistence/repository/PrescriptionItemRepository.java (modif.) + existsByMedicationId(Long)
  prescriptions/
    api/MedicationController.java                       (modif.) endpoints nuevos (criterio 2 y 3)
    api/dto/MedicationRequest.java                      (nuevo) name, presentation, concentration, route
    api/dto/MedicationPresetRequest.java                (nuevo) label, dose, frequency, duration, instructions
    api/dto/MedicationResponse.java                     (modif.) añadir al FINAL `boolean active`
    api/dto/MedicationPresetResponse.java               (modif.) añadir al FINAL `boolean active`
    application/MedicationService.java                  (modif.) la búsqueda solo devuelve presets activos
    application/MedicationCatalogService.java           (nuevo) create, update, setActive de medicamento y preset; listAll
    application/PrescriptionService.java                (modif.) preset inactivo en un ítem nuevo → 409
  audit/application/AuditService.java                   (modif.) AUDITED_ENTITIES: "medications" → Medication.class,
                                                        "medication-presets" → MedicationPreset.class
src/test/java/com/bellidental/prescriptions/MedicationCatalogIT.java (nuevo)
docs/IMPLEMENTATION-PLAN.md, docs/schema.dbml           (ya actualizados por Claude; no tocar)
```
No hay `domain/`: no son reglas puras. No hay `<Modulo>Directory` nuevo: ningún otro módulo usa esto.

## Criterios de aceptación
Respuestas con `ApiResponse`; errores con `BusinessException` (409), `InvalidInputException` (400),
`ResourceNotFoundException` (404), como el resto del proyecto. Todo texto entra con `trim`.

1. **V20** (`V20__medication_catalog_admin.sql`):
   - `ALTER TABLE medication_presets ADD COLUMN active boolean NOT NULL DEFAULT TRUE;`
   - `medications_aud` y `medication_presets_aud` con el mismo estilo que V17: una columna por campo mapeado, mismo
     tipo SQL, **sin NOT NULL/DEFAULT/CHECK/FK/UNIQUE**, más `rev bigint NOT NULL REFERENCES revision_info(revision_id)`,
     `revtype smallint` y `PRIMARY KEY (<id>, rev)`.
     `medications_aud`: `medication_id bigint, name varchar(150), presentation varchar(100), concentration varchar(100),
     route varchar(50), active boolean`. `medication_presets_aud`: `medication_preset_id bigint, medication_id bigint,
     label varchar(60), dose varchar(100), frequency varchar(100), duration varchar(100), instructions text,
     active boolean`.
   - `ddl-auto: validate` debe pasar; si falla se corrige V20 (nunca se desactiva la validación).
2. **Medicamentos** (`RECORD_CREATE`):
   - `GET /api/v1/medications/manage` → todos (activos e inactivos), ordenados por nombre y concentración, cada uno con
     **todos** sus presets (con `active`).
   - `POST /api/v1/medications` → 201 `MedicationResponse`. `name`, `presentation`, `concentration` `@NotBlank`
     (`@Size` 150/100/100); `route` opcional (`@Size` 50). Mismo nombre + concentración + presentación (sin distinguir
     mayúsculas) → 409 "Ya existe ese medicamento con esa presentación y concentración.".
   - `PUT /api/v1/medications/{id}` → 200, mismas validaciones (excluyendo el propio id). Si el medicamento ya está en
     algún `prescription_items` → 409 "El medicamento ya se usó en recetas: desactívalo y crea uno nuevo.".
   - `POST /api/v1/medications/{id}/deactivate` y `/activate` → 200 `MedicationResponse`.
   - No existe → 404.
3. **Posologías** (`RECORD_CREATE`):
   - `POST /api/v1/medications/{id}/presets` → 201 `MedicationPresetResponse`. `label` `@NotBlank @Size(max=60)`;
     `dose`, `frequency`, `duration` `@NotBlank @Size(max=100)`; `instructions` opcional. Medicamento inactivo → 409
     "El medicamento está desactivado.". Misma `label` en el mismo medicamento (sin distinguir mayúsculas) → 409
     "Ese medicamento ya tiene una posología con esa etiqueta.".
   - `PUT /api/v1/medication-presets/{id}` → 200, mismas validaciones (excluyendo el propio id).
   - `POST /api/v1/medication-presets/{id}/deactivate` y `/activate` → 200.
   - No existe → 404.
4. **Lectura para recetar**: `GET /api/v1/medications?query=` sigue devolviendo solo medicamentos activos y ahora
   solo sus presets **activos**. Un ítem nuevo de receta con un preset inactivo → 409 "La posología está desactivada.".
5. Los cambios quedan en Envers y se ven en `GET /audit` con las entidades `medications` y `medication-presets`.
6. Sin `professional_id`: el catálogo es uno solo para toda la clínica.

## Tests requeridos
**Unit:** ninguno.

**Integration** — `MedicationCatalogIT` (3 casos, con el odontólogo de la semilla):
1. `POST /medications` + `POST /medications/{id}/presets` → `GET /medications?query=<nombre>` lo devuelve con el preset;
   `PUT /medication-presets/{id}` cambia la dosis y la búsqueda la refleja.
2. `POST /medication-presets/{id}/deactivate` → ya no sale en `GET /medications?query=`, sí en `GET /medications/manage`
   con `active=false`.
3. Medicamento usado en un ítem de receta (crear la receta por API, como en `PrescriptionFlowIT`) →
   `PUT /medications/{id}` → 409 con el mensaje.

## Resultado
**Archivos creados:**
- `src/main/resources/db/migration/V20__medication_catalog_admin.sql` (columna `active` en `medication_presets`; `medications_aud` y `medication_presets_aud` al estilo de V17).
- `prescriptions/api/dto/MedicationRequest.java`, `MedicationPresetRequest.java`.
- `prescriptions/application/MedicationCatalogService.java` (listAll, create/update/setActive de medicamento; createPreset/updatePreset/setPresetActive de posología).
- `src/test/java/com/bellidental/prescriptions/MedicationCatalogIT.java` (3 casos).

**Archivos modificados:**
- `persistence/entity/Medication.java` (`@Audited`), `persistence/entity/MedicationPreset.java` (`@Audited`, campo `active`).
- `persistence/repository/MedicationRepository.java`, `MedicationPresetRepository.java`, `PrescriptionItemRepository.java` (consultas del plan).
- `prescriptions/api/MedicationController.java` (endpoints de manage, alta/edición/activación de medicamentos y posologías; permiso `RECORD_CREATE`).
- `prescriptions/api/dto/MedicationResponse.java`, `MedicationPresetResponse.java` (`boolean active` al final).
- `prescriptions/application/MedicationService.java` (búsqueda solo con presets activos; mapeo a respuesta reutilizado por el servicio nuevo).
- `prescriptions/application/PrescriptionService.java` (preset inactivo al recetar → 409 "La posología está desactivada.").
- `audit/application/AuditService.java` (`medications`, `medication-presets` en `AUDITED_ENTITIES`).

**Tests:**
- Failsafe (`MedicationCatalogIT`): 3 tests, 0 fallos, 0 errores.
- Verificación final `./mvnw -q clean verify` (una vez): exit 0, sin errores de build.
- Totales de `target/surefire-reports/*.txt`: 24 clases, 103 tests, 0 fallos, 0 errores, 0 omitidos.
- Totales de `target/failsafe-reports/*.txt`: 27 clases, 89 tests, 0 fallos, 0 errores, 0 omitidos.

**Problemas y supuestos:**
- No hay problemas de compilación ni de `ddl-auto: validate`; V20 se aplicó sin cambios.
- `MedicationService` expone `toResponse`/`toPresetResponse` como visibilidad de paquete para no duplicar el mapeo en `MedicationCatalogService`.
- Trim: los textos obligatorios se recortan en el servicio; `route`, `instructions` en blanco se guardan como `null`. La validación `@Size` se aplica sobre el texto sin recortar.
- El PUT de medicamento valida primero "usado en recetas" y luego duplicados.
- El preset no se puede crear ni reactivar bajo un medicamento desactivado solo en el alta (criterio 3); la reactivación de un preset no comprueba el estado del medicamento (no estaba especificado).
- `MedicationCatalogIT` usa el día +210 y horas desde las 8:00 para no chocar con las agendas de otros ITs.
- No se hizo commit.

## Revisión
Estado: APROBADO

Cumple los 6 criterios y los 3 casos de `MedicationCatalogIT`. V20 sigue el estilo de V17; la búsqueda solo devuelve
presets activos; medicamento usado en recetas → 409. Corrección menor hecha por Claude: `MedicationController`
usaba `import org.springframework.web.bind.annotation.*` → imports explícitos.

`./mvnw -q clean verify` (Claude): Surefire 103/0/0/0 · Failsafe 89/0/0/0.
