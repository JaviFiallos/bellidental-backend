---
name: testing
description: Reglas de tests del backend Bellidental. Usar al escribir o correr tests.
---
- Quien: Gemini escribe y corre los tests de cada tarea. Cuando: en la misma tarea que implementa el módulo, nunca después.
- Unit tests (JUnit 5 + Mockito) para la lógica de negocio de cada servicio. Clase `*Test`, corren con surefire (`./mvnw test`).
- Integration tests mínimos, no exhaustivos. Solo en los módulos críticos del sistema:
  esquema/semillas, autenticación, citas (cruces) y atención clínica. El resto de módulos no lleva `*IT`.
  - Solo los `*IT` listados en `## Tests requeridos`; no añadir otros por iniciativa propia.
  - Pocos casos por `*IT`: el flujo principal y el fallo que más importa, no todas las combinaciones.
  - Clase `*IT`, corren con failsafe (`./mvnw verify`).
- Integración con PostgreSQL real vía Testcontainers (`postgres:16-alpine`); nunca H2.
- Todo `*IT` usa la configuración compartida `com.bellidental.support.TestcontainersConfiguration`
  (`@Import`, `@ServiceConnection`); no declarar contenedores propios.
- No testear getters, entidades simples, DTOs ni código de glue.
- Nombre: `metodo_condicion_resultadoEsperado`.
- Reportar el resumen real de la salida de los tests, no inventado.
- Un test que falla no se borra ni se desactiva: se reporta en Resultado.
