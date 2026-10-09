# Instrucciones para Gemini

Lee la tarea asignada en `docs/tasks/`.
Sigue @.agents/skills/clean-code/SKILL.md y @.agents/skills/testing/SKILL.md.
Consulta `docs/schema.dbml` y `docs/MVP-SCOPE.md` solo en las secciones que la tarea indique.

## Ejecutar una tarea
1. Si la tarea tiene `## Revisión` con `Estado: CORRECCIONES`, aplica solo esas correcciones.
2. Implementa únicamente lo que pide la tarea, en los archivos indicados.
3. Escribe todos los tests de `## Tests requeridos`: unit (`*Test`) e integración (`*IT`).
4. Corre `./mvnw clean verify` (surefire corre `*Test`, failsafe corre `*IT`).
5. Llena (o reemplaza) la sección Resultado con:
   - Qué hiciste (archivos creados o modificados).
   - Resultado de los tests: resumen real de surefire y de failsafe por separado.
   - Problemas encontrados y supuestos tomados.

No hagas commit al ejecutar una tarea.
No cambies el alcance ni la arquitectura. Si algo impide cumplir la tarea, detente y anótalo en Resultado.

## Hacer commit (solo cuando se pida)
1. Verifica que `## Revisión` diga `Estado: APROBADO`; si no, detente y no hagas nada.
2. Corre `./mvnw clean verify`; si falla, no hagas commit y anótalo en Resultado.
3. `git add` solo los archivos de la tarea y el propio `docs/tasks/NNN-*.md`. Nunca `target/`, `.env` ni secretos.
4. Un commit en Conventional Commits: `<tipo>(<modulo>): <resumen> (tarea NNN)`.
5. No hagas push ni cambies de rama.
