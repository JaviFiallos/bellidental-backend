# 009 — Odontograma (V8)

## Objetivo
Módulo `com.bellidental.odontogram` (RF02; plan §4.5; D-6, D-14). El odontólogo registra el odontograma del 033
por pieza, cara y rango de piezas, con toda la simbología clínica y el registro periodontal (movilidad, recesión).
Se expone el estado vigente del paciente y sus índices CPO/ceo.

Correcciones (D-6): el odontólogo que atendió puede anular hallazgos y registrar el correcto **en la misma
atención**, también después del cierre (`COMPLETED`). Es la misma regla que los diagnósticos de la 008
(`EncounterPolicy.checkCanModify`), y el módulo clínico la expone para no duplicarla en 009, 010 y 011.

Valores de D-14b (pendiente con la odontóloga): se usa la propuesta, CROWN = O/o. Si cambia, se corrige con una
migración de datos, sin cambiar columnas.

Fuera de alcance: `OdontogramDirectory` (llega en la 010, cuando tratamientos necesite leer y crear hallazgos),
`clinical-summary` y Envers.

## Archivos a tocar
```
src/main/resources/db/migration/V8__odontogram.sql                       (nuevo) tablas + semillas
shared/enums/                                                            (nuevos)
  FindingStatus.java        PENDING, DONE
  ConditionScope.java       TOOTH, SURFACE, SPAN
  ConditionCategory.java    DENTAL, PERIODONTAL
  Dentition.java            PERMANENT, DECIDUOUS
  Arch.java                 UPPER, LOWER
  ToothType.java            INCISOR, CANINE, PREMOLAR, MOLAR
persistence/entity/         Tooth, ToothSurface, DentalCondition, DentalConditionSymbol, DentalFinding  (nuevos)
persistence/repository/     uno por entidad                                                         (nuevos)
clinical/                                                                (cambio mínimo)
  ClinicalDirectory.java         + EncounterSummary requireModifiableEncounter(UUID encounterId)
  application/EncounterService.java, ClinicalDirectoryImpl.java   implementan el método nuevo
odontogram/                                                              (nuevo)
  api/
    OdontogramController.java
  api/dto/
    CreateFindingRequest, FindingResponse, SymbolResponse, CatalogResponse, OdontogramResponse, DentalIndexResponse
  application/
    OdontogramService.java       catálogo, registrar, anular, listar por atención, odontograma del paciente
  domain/
    OdontogramFinding.java       record con lo que necesitan las reglas (pieza, pieza final, cara, condición,
                                 scope, componentes cpo/ceo, dentición, arcada, tipo, estado, createdAt)
    FindingRules.java            validación de un hallazgo nuevo (scope, arcada, grado, símbolo, notas)
    OdontogramStateCalculator.java   estado vigente
    DentalIndexCalculator.java       CPO, ceo y caras con caries por pieza
    SurfaceLabelResolver.java        etiqueta de cara por pieza
src/test/java/com/bellidental/odontogram/
  domain/OdontogramStateCalculatorTest, domain/DentalIndexCalculatorTest, OdontogramFlowIT
```
Depende de `ClinicalDirectory` (`findEncounter`, `requireModifiableEncounter`) y `PatientDirectory` (`findById`).
No importa `api`, `application` ni `domain` de otros módulos. `domain/` es Java puro (sin Spring ni repositorios).

## Criterios de aceptación
**Migración V8** (columnas exactas de `docs/schema.dbml`, sección 7)
1. `teeth` con las 52 piezas FDI: permanentes 11–18, 21–28, 31–38, 41–48 y temporales 51–55, 61–65, 71–75, 81–85.
   - `quadrant` y `position` = primer y segundo dígito; `dentition` PERMANENT (1–4) / DECIDUOUS (5–8);
   - `arch`: cuadrantes 1, 2, 5, 6 → UPPER; 3, 4, 7, 8 → LOWER;
   - `tooth_type`: permanentes 1–2 INCISOR, 3 CANINE, 4–5 PREMOLAR, 6–8 MOLAR; temporales 1–2 INCISOR, 3 CANINE,
     4–5 MOLAR.
2. `tooth_surfaces`: O Oclusal, V Vestibular, L Lingual, M Mesial, D Distal.
3. `dental_conditions`. CHECKs:
   - `category IN ('DENTAL','PERIODONTAL')`, `scope IN ('TOOTH','SURFACE','SPAN')`;
   - `cpo_component IN ('C','P','O')` y `ceo_component IN ('c','e','o')`, ambos nulos permitidos;
   - `grade_min` y `grade_max` ambos nulos, o ambos presentes con `grade_min <= grade_max`.
4. `dental_condition_symbols`, UNIQUE `(dental_condition_id, status)`. CHECKs:
   - `status IN ('PENDING','DONE')`, `line_style IN ('SOLID','DOTTED')`, `shape` en la lista del dbml;
   - `color` y `secondary_color` con `~ '^#[0-9A-F]{6}$'`.
5. `dental_findings`, FKs a `encounters`, `teeth` (`tooth_code` y `end_tooth_code`), `tooth_surfaces` y
   `dental_conditions`. CHECKs:
   - `status IN ('PENDING','DONE')`;
   - `end_tooth_code IS NULL OR (surface_code IS NULL AND end_tooth_code <> tooth_code)`.

   Índices: `(encounter_id)`, `(tooth_code)`.
6. Semilla con ids fijos y `setval` de las secuencias (como la tarea 002). Colores: rojo `#D32F2F`, azul `#1E88E5`,
   negro `#000000`. Una fila de símbolo por cada celda PENDING/DONE con valor; un guion significa que no hay fila:

   | id | code | name | category | scope | grado | cpo | ceo | PENDING | DONE |
   |---|---|---|---|---|---|---|---|---|---|
   | 1 | MOBILITY | Movilidad | PERIODONTAL | TOOTH | 0–3 | — | — | — | NUMBER azul |
   | 2 | RECESSION | Recesión gingival | PERIODONTAL | TOOTH | 1–4 | — | — | — | NUMBER azul |
   | 3 | CARIES | Caries | DENTAL | SURFACE | — | C | c | CIRCLE rojo | — |
   | 4 | FILLING | Obturación | DENTAL | SURFACE | — | O | o | — | CIRCLE azul |
   | 5 | SEALANT | Sellante | DENTAL | SURFACE | — | — | — | ASTERISK rojo | ASTERISK azul |
   | 6 | EXTRACTION | Extracción indicada | DENTAL | TOOTH | — | P | e | CROSS rojo | — |
   | 7 | MISSING_CARIES | Pérdida por caries | DENTAL | TOOTH | — | P | — | — | CROSS azul |
   | 8 | MISSING_OTHER | Pérdida por otra causa | DENTAL | TOOTH | — | — | — | — | CIRCLE_CROSS azul, secundario negro |
   | 9 | ROOT_CANAL | Endodoncia | DENTAL | TOOTH | — | — | — | TRIANGLE rojo | TRIANGLE azul |
   | 10 | CROWN | Corona | DENTAL | TOOTH | — | O | o | — | DOUBLE_SQUARE azul |
   | 11 | FIXED_PROSTHESIS | Prótesis fija | DENTAL | SPAN | — | — | — | — | SQUARES_LINE negro, DOTTED |
   | 12 | REMOVABLE_PROSTHESIS | Prótesis removible | DENTAL | SPAN | — | — | — | — | DOTS_PARENTHESES negro |
   | 13 | TOTAL_PROSTHESIS | Prótesis total | DENTAL | SPAN | — | — | — | — | EQUALS negro |
   | 14 | OTHER | Otro (patología o tratamiento) | DENTAL | TOOTH | — | — | — | RECTANGLE rojo, `filled` | RECTANGLE azul, `filled` |

   Las demás filas de símbolo: `filled = false`, `line_style = SOLID`. No tocar V1–V7_1.
7. Entidades: ids de otras tablas como columnas simples (sin `@ManyToOne`, como `Encounter`); enums de
   `shared/enums` con `@Enumerated(STRING)`; `shape`, `line_style` y colores como `String`; `OffsetDateTime`,
   `Long` (catálogos), `Short` (`tooth_code`, `grade`), `UUID`. Hibernate `validate` pasa.

**Corrección en el módulo clínico (D-6)**
8. `ClinicalDirectory.requireModifiableEncounter(UUID encounterId)` → `EncounterSummary`. En `EncounterService`,
   el mismo orden que ya usan sus escrituras: atención inexistente → 404; usuario sin profesional → 409;
   `EncounterPolicy.checkCanModify` (anulada u otro profesional → 409). Permitido con `OPEN` y `COMPLETED`.
   `ClinicalDirectoryImpl` delega, sin reglas propias. No cambia nada más del módulo clínico.

**Endpoints** (`hasAuthority`; la secretaria no tiene `RECORD_*` y recibe 403 sin código extra)
9. `GET /api/v1/odontogram/catalog` (RECORD_READ): piezas (código, dentición, arcada, cuadrante, posición, tipo),
   caras y condiciones activas con sus símbolos.
10. `POST /api/v1/encounters/{id}/findings` (RECORD_CREATE), 201. `CreateFindingRequest`: `toothCode`, `conditionId`,
    `status` obligatorios; `endToothCode`, `surfaceCode`, `grade`, `notes` opcionales. En este orden:
    1. `ClinicalDirectory.requireModifiableEncounter` (404 / 409). Con la atención `COMPLETED` se permite: es una
       corrección (D-6);
    2. condición inexistente o inactiva, pieza o pieza final inexistente, cara inexistente → 400;
    3. `FindingRules` (`InvalidInputException`, 400):
       - SURFACE exige `surfaceCode` y prohíbe `endToothCode`; TOOTH prohíbe ambos;
       - SPAN exige `endToothCode` de la misma arcada y distinto de `toothCode`; prohíbe `surfaceCode`;
       - grado obligatorio y dentro de `[grade_min, grade_max]` si la condición lo define; prohibido si no;
       - el par (condición, estado) debe tener símbolo (p. ej. CARIES DONE → 400);
       - OTHER exige `notes` no vacías.
    4. guardar con `created_at` = ahora.
11. `GET /api/v1/encounters/{id}/findings` (RECORD_READ): atención inexistente → 404 (`findEncounter`); devuelve los
    hallazgos no anulados de la atención, con símbolo y etiqueta de cara.
12. `POST /api/v1/findings/{id}/void` (RECORD_UPDATE), 200. Sin cuerpo (`dental_findings` no tiene `void_reason`):
    - hallazgo inexistente → 404; ya anulado → 409;
    - `requireModifiableEncounter` sobre su atención: la anula el odontólogo que atendió, **también con la atención
      `COMPLETED`** (D-6);
    - fija `voided_at` = ahora. Sin `DELETE`.
13. `GET /api/v1/patients/{id}/odontogram` (RECORD_READ). Paciente inexistente → 404 (`PatientDirectory`).
    - Hallazgos del paciente: consulta en `DentalFindingRepository` que une `encounters` y `appointments` por sus
      columnas de id, excluye hallazgos anulados y atenciones anuladas, y ordena por `created_at`.
    - **Estado vigente** (`OdontogramStateCalculator`): el último gana por clave. SURFACE: (pieza, cara);
      TOOTH: (pieza, condición); SPAN: (pieza, pieza final, condición). Una pieza con `MISSING_CARIES` o
      `MISSING_OTHER` vigente no muestra sus demás hallazgos TOOTH ni SURFACE; los SPAN se mantienen.
    - **Índices** (`DentalIndexCalculator`, sobre el estado vigente): CPO en permanentes con `cpo_component`
      (C, P, O) y ceo en temporales con `ceo_component` (c, e, o). Cada pieza cuenta una vez por componente;
      se devuelve el total de cada índice.
    - `cariesSurfaces`: por pieza, número de caras con CARIES vigente (1 simple, 2 compuesta, 3 o más compleja).
    - Cada hallazgo lleva su símbolo y la etiqueta de cara resuelta.
14. Etiqueta de cara (`SurfaceLabelResolver`): L → "Palatina" en arcada superior y "Lingual" en inferior;
    O → "Incisal" en INCISOR y CANINE, "Oclusal" en las demás; V, M y D sin cambio.

**General**
15. Errores con `GlobalExceptionHandler`. No se tocan otros módulos (salvo el punto 8) ni migraciones anteriores.

## Tests requeridos
- Unit `domain/OdontogramStateCalculatorTest` (2 casos):
  - el último hallazgo gana por (pieza, cara);
  - MISSING_CARIES oculta la caries de esa pieza, pero no el SPAN que la incluye.
- Unit `domain/DentalIndexCalculatorTest` (2 casos):
  - pieza con 2 caras cariadas → C = 1 y `cariesSurfaces` = 2;
  - EXTRACTION en permanente → P; en temporal → e.
- Integration `OdontogramFlowIT` (`TestcontainersConfiguration`, login por HTTP). Setup como `EncounterFlowIT`: la
  secretaria crea un paciente; la odontóloga crea una cita (origen `PROFESSIONAL`), le hace check-in y abre la atención.
  Casos:
  1. `GET /odontogram/catalog` → 52 piezas, 5 caras, 14 condiciones, cada una con al menos un símbolo;
  2. corrección después del cierre: la odontóloga registra CARIES PENDING en 16-O y 16-M → 201; añade un diagnóstico y
     completa la atención → 200; anula el hallazgo 16-M → 200 y registra FILLING DONE en 16-M → 201 (atención
     `COMPLETED`); `GET /patients/{id}/odontogram` → 16-M es FILLING, `cariesSurfaces` de 16 = 1, CPO C = 1;
  3. la secretaria `POST /encounters/{id}/findings` → 403.

## Resultado

## Revisión
