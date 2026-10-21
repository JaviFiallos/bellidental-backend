---
name: testing
description: Reglas de tests del backend Bellidental. Usar al escribir o correr tests.
---
Principio: pocos tests y solo los esenciales. La verificación principal es la integración (flujo HTTP real);
los unit tests son la excepción.

- Quién: Gemini escribe y corre los tests de cada tarea, en la misma tarea que implementa el módulo.
- Escribir solo los tests listados en `## Tests requeridos`; nunca añadir otros por iniciativa propia.

**Integración (`*IT`, failsafe) — el foco**
- Un `*IT` por tarea que expone endpoints, con 2–3 casos: el flujo principal de punta a punta por HTTP y el fallo
  de negocio que más importa (cruce, permiso, estado inválido). No todas las combinaciones ni cada validación.
- PostgreSQL real vía Testcontainers (`postgres:16-alpine`); nunca H2. Todo `*IT` usa
  `com.bellidental.support.TestcontainersConfiguration` (`@Import`, `@ServiceConnection`); sin contenedores propios
  ni `@MockitoBean` que rompan el contexto compartido.
- Datos del setup vía API o semillas; nada de SQL manual salvo para probar restricciones de esquema.

**Unit (`*Test`, surefire) — solo lo esencial**
- Solo para reglas puras de `domain/` cuyo error es caro y difícil de ver por HTTP (máquina de estados, cálculo de
  huecos, CPO, validadores como la cédula). Máximo 4 casos por tarea en total.
- Sin tests de servicios con mocks salvo que la tarea lo pida expresamente. No testear getters, entidades, DTOs,
  controladores ni validaciones simples (`@NotNull`, rangos, campos obligatorios).

**Correr tests (ahorrar tokens)**
- Durante el desarrollo, solo los tests de la tarea: `./mvnw -q verify -Dtest=<Clase>Test -Dit.test=<Clase>IT
  -Dsurefire.failIfNoSpecifiedTests=false`.
- Al final, una sola vez: `./mvnw -q clean verify`. No pegar el log completo: si algo falla, leer solo el
  `target/surefire-reports/*.txt` o `target/failsafe-reports/*.txt` de la clase que falla.

**General**
- Nombre: `metodo_condicion_resultadoEsperado`.
- Reportar el resumen real (totales de surefire y failsafe), no inventado.
- Un test que falla no se borra ni se desactiva: se reporta en Resultado.
- Los tests ya existentes no se borran salvo en una tarea dedicada.
