# Instrucciones para Gemini

Lee la tarea asignada en `docs/tasks/`.
Sigue @.agents/skills/architecture/SKILL.md, @.agents/skills/clean-code/SKILL.md y @.agents/skills/testing/SKILL.md.
Crea los archivos exactamente en los paquetes que indica `## Archivos a tocar`.
Consulta `docs/schema.dbml` y `docs/MVP-SCOPE.md` solo en las secciones que la tarea indique.

## Ejecutar una tarea
1. Si la tarea tiene `## Revisión` con `Estado: CORRECCIONES`, aplica solo esas correcciones.
2. Implementa únicamente lo que pide la tarea, en los archivos indicados.
3. Escribe solo los tests de `## Tests requeridos`, ni uno más (ver `testing/SKILL.md`).
4. Mientras desarrollas corre solo los tests de la tarea; al final, una vez, `./mvnw -q clean verify`.
   No pegues el log completo: si algo falla, lee solo el report de la clase que falla.
5. Llena (o reemplaza) la sección Resultado con:
   - Qué hiciste (archivos creados o modificados).
   - Resultado de los tests: resumen real de surefire y de failsafe por separado.
   - Problemas encontrados y supuestos tomados.

No hagas commit al ejecutar una tarea.
No cambies el alcance ni la arquitectura. Si algo impide cumplir la tarea, detente y anótalo en Resultado.

## Hacer commit (solo cuando se pida)
1. Verifica que `## Revisión` diga `Estado: APROBADO`; si no, detente y no hagas nada.
2. Corre `./mvnw -q clean verify`; si falla, no hagas commit y anótalo en Resultado.
3. `git add` solo los archivos de la tarea y el propio `docs/tasks/NNN-*.md`. Nunca `target/`, `.env` ni secretos.
4. Un commit en Conventional Commits: `<tipo>(<modulo>): <resumen> (tarea NNN)`.
5. No hagas push ni cambies de rama.
