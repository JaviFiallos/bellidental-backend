# 007a — CIE-10: redacción oficial OPS y términos incluidos
> Pedido del usuario 2026-10-19: la clínica usa la lista oficial en español (OPS) de K00–K14. Ningún código faltaba;
> cambia la redacción de 47 títulos y 3 categorías, y se agregan los términos incluidos para la búsqueda predictiva
> (p. ej. «fluorosis» → K00.3, «ránula» → K11.6). Se conservan K02.5, K12.3 y Z01.2. El endpoint, los permisos y el
> frontend no cambian. Plan: §4.4 (párrafo «CIE-10 OPS»).

## Objetivo
1. **Migración V14** (`V14__icd10_ops_terms.sql`, **ya generada por Claude** desde la lista de la clínica; no
   editar los datos): `ALTER TABLE icd10_codes ADD COLUMN included_terms varchar(500)`, índice GIN trigram,
   `UPDATE` de títulos, categorías e `included_terms` (74 códigos).
2. **Entidad** `Icd10Code`: campo `includedTerms` (`@Column(name = "included_terms", length = 500)`).
3. **Búsqueda por texto** (`Icd10CodeRepository.findActiveByDescription`): además de `description`, buscar en
   `included_terms` con `ILIKE '%' || :text || '%'` y `:text <% included_terms` (word similarity). Orden:
   `GREATEST(similarity(description, :text), word_similarity(:text, coalesce(included_terms, '')))` desc, luego
   `code`; máx. 20. La búsqueda por prefijo de código (`K02` → K02.0…K02.9) no cambia.
4. **Respuesta** `Icd10Response`: agregar `includedTerms` (puede ser `null`) al final del record.
   `description` sigue siendo solo el título; nada cambia en receta, historial ni 033.

## Archivos a tocar
```
src/main/java/com/bellidental/
├── persistence/entity/Icd10Code.java                 (+ includedTerms)
├── persistence/repository/Icd10CodeRepository.java   (búsqueda por texto también en included_terms)
└── clinical/
    ├── api/dto/Icd10Response.java                    (+ includedTerms)
    └── application/Icd10Service.java                 (mapear includedTerms)
src/main/resources/db/migration/V14__icd10_ops_terms.sql   (ya existe; no tocar)
src/test/java/com/bellidental/clinical/Icd10SearchIT.java  (+1 caso)
```
- Docs ya actualizados por Claude: `schema.dbml` (columna e índice), `IMPLEMENTATION-PLAN.md` (fila 007a, §4.4,
  cobros → V15, auditoría → V16), `MVP-SCOPE.md`.
- Sin cambios en `ClinicalDirectory` ni en otros módulos.

## Criterios de aceptación
1. `GET /api/v1/icd10?query=fluorosis` (ODONTOLOGO) devuelve K00.3 primero, con `description` = «Dientes moteados» e
   `includedTerms` que contiene «Fluorosis dental».
2. `query=K02` sigue devolviendo K02.0…K02.9 ordenados (K02.5 incluido); secretaria → 403.
3. Los títulos quedan con la redacción OPS (p. ej. K06.0 «Retracción gingival») y K02.5, K12.3, Z01.2 siguen activos.
4. `./mvnw -q clean verify` en verde.

## Tests requeridos
**Integration** (`Icd10SearchIT`, los 3 casos actuales + 1):
- Nuevo `search_includedTermAsOdontologa_returnsFluorosisCode`: `query=fluorosis` → primer resultado K00.3,
  `description` «Dientes moteados», `includedTerms` contiene «Fluorosis dental».
- Si un caso actual compara una descripción que cambió en V14, actualizar solo el texto esperado.

**Unit:** ninguno.

## Resultado
Archivos cambiados:
- `persistence/entity/Icd10Code.java`: campo `includedTerms` (`included_terms`, 500).
- `persistence/repository/Icd10CodeRepository.java`: `findActiveByDescription` busca también en `included_terms` (ILIKE y `<%`); orden por `GREATEST(similarity, word_similarity(:text, coalesce(included_terms,'')))`.
- `clinical/api/dto/Icd10Response.java`: `includedTerms` al final del record.
- `clinical/application/Icd10Service.java`: mapea `includedTerms`.
- `test/.../clinical/Icd10SearchIT.java`: +1 caso `search_includedTermAsOdontologa_returnsFluorosisCode`. Los 3 casos actuales no requirieron cambios (K02.1 no cambia en V14).

Tests (`./mvnw -q clean verify`, exit 0):
- Surefire: 92 run, 0 failures, 0 errors, 0 skipped.
- Failsafe: 62 run, 0 failures, 0 errors, 0 skipped.
- `Icd10SearchIT` en particular: 4/4 verdes.

Problemas / supuestos:
- Primer intento de `Icd10SearchIT` falló al arrancar el contexto: Flyway "more than one migration with version 14". Causa: `target/classes/db/migration/V14__icd10_ops_wording.sql` es un borrador viejo que ya no existe en `src/`. Se resolvió con `clean` (no se tocó el código ni la migración de `src/`). Si vuelve a pasar sin `clean`, borrar `target/`.
- La migración `V14__icd10_ops_terms.sql` no se modificó. No hay commit.

## Revisión
Estado: APROBADO

Diff revisado (entidad, consulta nativa, respuesta, servicio, +1 caso en `Icd10SearchIT`); V14, `schema.dbml` y el
plan los dejó Claude. `./mvnw -q clean verify`: surefire 92/0/0/0, failsafe 62/0/0/0.
