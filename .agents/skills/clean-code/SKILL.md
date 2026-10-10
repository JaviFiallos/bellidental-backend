---
name: clean-code
description: Reglas de código del backend Bellidental (Spring Boot). Usar al escribir o modificar código.
---
- Nombres claros en inglés; nada de abreviaturas ni `data`, `info`, `tmp`.
- Funciones pequeñas con una sola responsabilidad; sin duplicación.
- Un módulo = un paquete `com.bellidental.<modulo>`. Los módulos no se importan entre sí: se comunican por interfaces públicas.
- Entidades JPA en `com.bellidental.persistence.entity`; repositorios en `persistence.repository`.
- Esquema solo por Flyway y según `docs/schema.dbml`; nunca cambiar columnas por iniciativa propia.
- Fechas `timestamptz` ↔ `OffsetDateTime`; ids `uuid` ↔ `UUID`; catálogos `bigint` ↔ `Long`.
- Sin `DELETE` sobre datos clínicos o pagos: se anulan con `voided_at`.
- No crear, borrar ni mover módulos fuera del alcance de la tarea.
- Usar `import`; nunca nombres totalmente calificados en el código (`org.springframework...`, `java.util...`),
  salvo choque real de nombres (p. ej. `User` de Spring Security vs. la entidad).
- Excepciones de entrada inválida: `shared/exception` (no `IllegalArgumentException` genérica hacia el handler).
