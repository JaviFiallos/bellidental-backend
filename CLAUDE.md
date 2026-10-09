# Flujo de trabajo

- Claude: planifica y revisa. No escribe código salvo correcciones mínimas.
- Gemini (`gemini -p "..."`): ejecuta tareas, escribe código y tests (unit e integración), verifica y hace commit tras la aprobación.
- Alcance: `docs/MVP-SCOPE.md`. Diseño de datos: `docs/schema.dbml`.
- Contexto del dominio: `docs/CONTEXT.md`. Plan por módulo y decisiones pendientes: `docs/IMPLEMENTATION-PLAN.md`
  (cada tarea se redacta desde su §4.x; no releer `proy-infor/`).

## Ciclo
1. Claude crea `docs/tasks/NNN-nombre.md` (una tarea = un módulo), con sus tests.
   Integración (`*IT`) solo en módulos críticos (esquema/semillas, auth, citas, atención clínica) y con pocos casos;
   los demás módulos solo llevan unit tests de su lógica de negocio.
2. Ejecutar: `gemini -p "Ejecuta docs/tasks/NNN-nombre.md siguiendo GEMINI.md."`
3. Claude revisa la sección Resultado y `git diff --stat` (y los archivos críticos si hace falta).
4. Claude escribe `## Revisión`:
   - No cumple → `Estado: CORRECCIONES` + lista numerada → volver al paso 2.
   - Cumple → `Estado: APROBADO`.
5. Commit: `gemini -p "Haz commit de la tarea docs/tasks/NNN-nombre.md siguiendo GEMINI.md."`
6. Claude verifica con `git log -1 --stat` y crea la siguiente tarea.

## Formato de tarea
`## Objetivo` · `## Archivos a tocar` · `## Criterios de aceptación` · `## Tests requeridos` (Unit / Integration) ·
`## Resultado` (lo llena Gemini) · `## Revisión` (lo llena Claude)

Reglas: @.agents/skills/clean-code/SKILL.md · @.agents/skills/testing/SKILL.md
Ahorrar tokens: mensajes breves, no leer código innecesario, confiar en el Resultado.
