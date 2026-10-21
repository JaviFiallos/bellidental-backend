# 008b — Atención: casillas del 033 (regiones del examen y exámenes del plan) y descripción única de planes

## Objetivo
El 033 tiene casillas que hoy no se guardan:
- **Sección 5, Examen del sistema estomatognático:** 12 regiones numeradas (1 Labios … 12 Ganglios) que se marcan;
  debajo, la descripción de la patología "anotando el número" (ya existe: `stomatognathic_exam`, texto).
- **Sección 10, Planes de diagnóstico, terapéutico y educacional:** 4 casillas (Biometría · Química sanguínea ·
  Rayos X · Otros) y debajo **una sola descripción**. Hoy hay tres textos (`diagnostic_plan`, `therapeutic_plan`,
  `educational_plan`); el usuario pide un solo campo, como en el papel (2026-10-20).

Hoy el frontend adivina las casillas de la sección 10 buscando palabras en `diagnosticPlan` ("Otros" nunca se
marca) y deja la sección 5 sin marcar. Esta tarea guarda las casillas como datos de la atención y reemplaza los tres
textos de planes por `planDescription`, en el mismo `PUT /encounters/{id}` (sin endpoint nuevo).

## Contrato
`PUT /api/v1/encounters/{id}` (`UpdateEncounterRequest`) y `EncounterResponse` (GET, PUT, open, complete):

| Campo | Cambio | Tipo | Valores |
|---|---|---|---|
| `stomatognathicRegions` | nuevo | `number[]` | enteros 1–12 (numeración del 033) |
| `diagnosticExams` | nuevo | `string[]` | `BIOMETRIA` · `QUIMICA_SANGUINEA` · `RAYOS_X` · `OTROS` |
| `planDescription` | nuevo | texto | libre (descripción de la sección 10) |
| `diagnosticPlan`, `therapeuticPlan`, `educationalPlan` | **se eliminan** | — | — |

- El PUT reemplaza la cabecera completa: listas `null` o ausentes se guardan vacías.
- Se guardan sin repetidos y ordenadas (regiones de menor a mayor; exámenes en el orden de la tabla).
- La respuesta siempre trae listas, nunca `null`: `[]` si no hay ninguna marca.
- Una región fuera de 1–12 o un examen desconocido → 400 con el mensaje de validación de siempre.
- El único cliente es el frontend (tarea 021, que se ejecuta justo después); el agente no usa estos campos.

## Archivos a tocar
```
src/main/resources/db/migration/
  V14_1__encounter_033_marks.sql        (nuevo) V14_1 para no mover V15/V16, reservadas a 012 y 013
src/main/java/com/bellidental/
  persistence/entity/Encounter.java     + stomatognathicRegions (Short[]), diagnosticExams (String[]),
                                          planDescription; − diagnosticPlan, therapeuticPlan, educationalPlan
  clinical/api/dto/
    UpdateEncounterRequest.java         − los 3 planes; + String planDescription,
                                          List<@Min(1) @Max(12) Short> stomatognathicRegions,
                                          List<@Pattern(regexp = "BIOMETRIA|QUIMICA_SANGUINEA|RAYOS_X|OTROS") String> diagnosticExams
    EncounterResponse.java              − los 3 planes; + List<Short> stomatognathicRegions (después de
                                          stomatognathicExam), List<String> diagnosticExams, String planDescription
                                          (donde estaban los planes)
  clinical/application/EncounterService.java
                                        update(): normaliza las listas (null → vacío, sin repetidos, ordenado) y
                                        asigna; el mapeo a EncounterResponse devuelve listas (nunca null)
src/test/java/com/bellidental/clinical/EncounterFlowIT.java
                                        + 1 caso (abajo)
docs/IMPLEMENTATION-PLAN.md             fila 008b en §3 y el DTO de la atención actualizado
```

### Migración `V14_1__encounter_033_marks.sql` (en este orden)
1. Columnas nuevas:
   ```sql
   ALTER TABLE encounters
       ADD COLUMN stomatognathic_regions smallint[]    NOT NULL DEFAULT '{}',
       ADD COLUMN diagnostic_exams       varchar(20)[] NOT NULL DEFAULT '{}',
       ADD COLUMN plan_description       text,
       ADD CONSTRAINT chk_encounters_stomatognathic_regions
           CHECK (stomatognathic_regions <@ ARRAY[1,2,3,4,5,6,7,8,9,10,11,12]::smallint[]),
       ADD CONSTRAINT chk_encounters_diagnostic_exams
           CHECK (diagnostic_exams <@ ARRAY['BIOMETRIA','QUIMICA_SANGUINEA','RAYOS_X','OTROS']::varchar(20)[]);
   ```
2. Relleno de `diagnostic_exams` desde `diagnostic_plan`, con el criterio que hoy usa la impresión, para que las
   atenciones existentes no pierdan sus X: `~* 'biometr[ií]a'` → `BIOMETRIA`; `~* 'qu[ií]mica sangu[ií]nea'` →
   `QUIMICA_SANGUINEA`; `~* '\mrx\M|rayos[ -]x'` → `RAYOS_X`. Las regiones quedan vacías.
3. Unión de los tres textos en `plan_description` (no se pierde nada; los vacíos se omiten):
   ```sql
   UPDATE encounters SET plan_description = NULLIF(concat_ws(E'\n',
       NULLIF(btrim(diagnostic_plan), ''),
       'Terapéutico: '  || NULLIF(btrim(therapeutic_plan), ''),
       'Educacional: '  || NULLIF(btrim(educational_plan), '')), '');
   ```
4. `ALTER TABLE encounters DROP COLUMN diagnostic_plan, DROP COLUMN therapeutic_plan, DROP COLUMN educational_plan;`

### Mapeo en la entidad
Hibernate 6.6 (Boot 3.4.1) mapea arrays de PostgreSQL con `@JdbcTypeCode(SqlTypes.ARRAY)`:
```java
@JdbcTypeCode(SqlTypes.ARRAY)
@Column(name = "stomatognathic_regions", nullable = false, columnDefinition = "smallint[]")
private Short[] stomatognathicRegions = new Short[0];
```
(igual para `diagnostic_exams` con `String[]` y `varchar(20)[]`). `ddl-auto: validate` debe seguir arrancando; si
el validador se queja del tipo, se ajusta `columnDefinition` (no se cambia `ddl-auto`).

## Criterios de aceptación
1. La migración corre sobre una base con datos (V1–V14 aplicadas): las filas existentes quedan con regiones `{}`,
   los exámenes que el relleno detecta y `plan_description` con los tres textos unidos; las tres columnas viejas ya
   no existen.
2. PUT con `stomatognathicRegions: [5, 1, 5]`, `diagnosticExams: ["RAYOS_X", "BIOMETRIA"]` y
   `planDescription: "Rx periapical de 36"` → 200 con `[1, 5]`, `["BIOMETRIA", "RAYOS_X"]` y el texto; el GET
   posterior devuelve lo mismo.
3. PUT sin las listas (o con `null`) → se guardan vacías y la respuesta trae `[]`.
4. PUT con la región 13 o el examen `"ECOGRAFIA"` → 400; no cambia nada.
5. Ningún DTO ni clase menciona ya `diagnosticPlan`, `therapeuticPlan` ni `educationalPlan`.
6. Las reglas de edición de la atención no cambian (solo el odontólogo que atiende, también con la atención
   completada; anulada → no se edita).
7. `./mvnw -q clean verify` en verde.

## Tests requeridos
- **Unit:** ninguno.
- **Integration:** `EncounterFlowIT`, un caso nuevo
  `updateEncounter_033Marks_normalizesAndValidates`: abre la atención → PUT con regiones `[5, 1, 5]`, exámenes
  `["RAYOS_X", "BIOMETRIA"]` y `planDescription` → verifica la respuesta ordenada, sin repetidos y con el texto →
  GET devuelve lo mismo → PUT con la región 13 → 400.

## Resultado
- **Archivos creados o modificados:**
  - `src/main/resources/db/migration/V14_1__encounter_033_marks.sql`: nueva migración que agrega `stomatognathic_regions` (smallint[] con constraint `<@ ARRAY[1..12]`), `diagnostic_exams` (varchar(20)[] con constraint), `plan_description` (text), rellena exámenes a partir de regex en `diagnostic_plan`, migra y concatena los 3 planes anteriores en `plan_description`, y elimina `diagnostic_plan`, `therapeutic_plan` y `educational_plan`.
  - `src/main/java/com/bellidental/persistence/entity/Encounter.java`: mapea `stomatognathicRegions` (`Short[]`, `@JdbcTypeCode(SqlTypes.ARRAY)`), `diagnosticExams` (`String[]`, `@JdbcTypeCode(SqlTypes.ARRAY)`) y `planDescription`; se eliminaron los 3 campos de planes antiguos.
  - `src/main/java/com/bellidental/clinical/api/dto/UpdateEncounterRequest.java`: agrega validaciones para `stomatognathicRegions` (elementos entre 1 y 12), `diagnosticExams` (pattern de exámenes permitidos) y `planDescription`; elimina los 3 planes.
  - `src/main/java/com/bellidental/clinical/api/dto/EncounterResponse.java`: reemplaza los 3 planes por `stomatognathicRegions` (después de `stomatognathicExam`), `diagnosticExams` y `planDescription`.
  - `src/main/java/com/bellidental/clinical/application/EncounterService.java`: normaliza en `update()` ambas listas (null o ausente → vacías, sin duplicados, regiones ordenadas de menor a mayor y exámenes en el orden oficial: `BIOMETRIA`, `QUIMICA_SANGUINEA`, `RAYOS_X`, `OTROS`); el mapeo en `toResponse()` siempre entrega listas no nulas (`List.of(...)`).
  - `src/test/java/com/bellidental/clinical/EncounterFlowIT.java`: agregado test de integración `updateEncounter_033Marks_normalizesAndValidates`.
  - `docs/IMPLEMENTATION-PLAN.md` y `docs/schema.dbml`: actualizados con los nuevos campos y constraints.
- **Resultado de los tests (`./mvnw -q clean verify`):**
  - Surefire: `Tests run: 92, Failures: 0, Errors: 0, Skipped: 0`
  - Failsafe: `Tests run: 63, Failures: 0, Errors: 0, Skipped: 0`
  - Total: 155 pruebas ejecutadas exitosamente sin errores ni fallos.
- **Problemas encontrados y supuestos tomados:**
  - `ddl-auto: validate` de Hibernate 6.6 validó correctamente las definiciones `smallint[]` y `varchar(20)[]` contra PostgreSQL 16 sin requerir ajustes especiales en dialectos.
  - No se realizó commit según las reglas de `GEMINI.md`.

## Revisión
Estado: APROBADO

- `./mvnw -q clean verify`: surefire 92/0/0/0, failsafe 63/0/0/0. Ninguna clase ni DTO menciona ya los tres planes.
- Migración probada a mano sobre una tabla con datos (PostgreSQL 16): relleno de exámenes y unión de los tres
  textos correctos (vacíos omitidos, `NULL` si no hay nada); los CHECK rechazan la región 13 y elementos `NULL`.
- Normalización (sin repetidos, orden de la tabla) y listas nunca `null` en la respuesta: correcto.
- Corrección mínima de Claude en el plan §4.4: se quitó «Solo mientras `status=OPEN`» (contradecía D-6 y el
  criterio 6); ahora remite a **Cierre (D-6)**.
- Observaciones (no bloquean): el relleno no detecta «radiografía» sin «Rx» (mismo criterio que la impresión);
  hasta la tarea 021 el frontend sigue enviando los tres planes, que el backend ignora (se perderían si se edita
  una atención en ese intervalo).
