# Flujo de trabajo

- Claude: planifica y revisa. No escribe código salvo correcciones mínimas.
- Gemini (`gemini -p "..."`): ejecuta tareas, escribe código y tests (unit e integración), verifica y hace commit tras la aprobación.
- Alcance: `docs/MVP-SCOPE.md`. Diseño de datos: `docs/schema.dbml`.
- Contexto del dominio: `docs/CONTEXT.md`. Plan por módulo y decisiones pendientes: `docs/IMPLEMENTATION-PLAN.md`
  (cada tarea se redacta desde su §4.x; no releer `proy-infor/`).

## Ciclo
1. Claude crea `docs/tasks/NNN-nombre.md` (una tarea = un módulo), con sus tests. Antes de redactarla, Claude
   define la estructura del módulo según `architecture/SKILL.md`: qué subpaquetes usa, qué expone en su
   `Directory` y de qué `Directory` de otros módulos depende. En `## Archivos a tocar` la escribe como árbol de paquetes.
   Tests mínimos según `testing/SKILL.md`: un `*IT` por tarea con endpoints (flujo principal + el fallo clave,
   2–3 casos) y unit tests solo para reglas puras críticas de `domain/` (máx. 4 casos). Nada más.
2. Ejecutar: `gemini -p "Ejecuta docs/tasks/NNN-nombre.md siguiendo GEMINI.md."`
3. Claude revisa la sección Resultado (con los totales copiados de la consola) y `git diff --stat` (y los archivos
   críticos si hace falta). No corre `./mvnw -q clean verify` salvo que (a) haya corregido código él mismo o
   (b) la tarea toque algo de toda la app: migraciones, seguridad (filtros, permisos, `SecurityConfig`), manejo
   global de errores o configuración. Si lo corre, una vez y solo los totales. El paso de commit de Gemini vuelve
   a correr la verificación.
4. Claude escribe `## Revisión`:
   - No cumple → `Estado: CORRECCIONES` + lista numerada → volver al paso 2.
   - Cumple → `Estado: APROBADO`.
5. Commit: `gemini -p "Haz commit de la tarea docs/tasks/NNN-nombre.md siguiendo GEMINI.md."`
6. Claude verifica con `git log -1 --stat` y crea la siguiente tarea.

## Formato de tarea
`## Objetivo` · `## Archivos a tocar` · `## Criterios de aceptación` · `## Tests requeridos` (Unit / Integration) ·
`## Resultado` (lo llena Gemini) · `## Revisión` (lo llena Claude)

Reglas: @.agents/skills/architecture/SKILL.md · @.agents/skills/clean-code/SKILL.md · @.agents/skills/testing/SKILL.md
Ahorrar tokens: mensajes breves, no leer código innecesario, confiar en el Resultado.
