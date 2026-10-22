# 021 — Catálogo de tratamientos y especialidades administrable por el ADMIN (V17)

## Objetivo
**Requerimiento nuevo del usuario**: hoy el catálogo tiene 12 tratamientos sembrados en V9 y 2 especialidades en V4, y la
API solo los lee. Cambiar un precio o agregar un tratamiento exige una migración. El director (ADMIN) debe poder
**crear, editar y desactivar** tratamientos y especialidades por API, y cada tratamiento puede (opcionalmente)
pertenecer a una especialidad para filtrar el catálogo en la atención. Decisión **D-27** (plan §6).

La relación profesional ↔ especialidad **ya existe** (`PUT /professionals/{id}` con `specialtyIds`, tarea 004): no se toca.

Reglas de fondo:
- Nunca `DELETE`: se desactiva (`active = false`), porque `performed_treatments` y `appointments` apuntan a estas filas.
- Cambiar `base_price` solo afecta a las atenciones futuras: `performed_treatments.price` ya guarda la copia.
- El `code` de un tratamiento **no se edita** después de crearlo (el bot lo referencia en
  `application.bot.public-treatments`).
- `treatment_catalog.specialty_id` es **opcional** y solo sirve para filtrar y agrupar: **no** restringe qué odontólogo
  puede registrar qué tratamiento. `NULL` = tratamiento de cualquier especialidad.
- Solo `hasRole('ADMIN')` en todo lo nuevo (sin permiso nuevo: no se toca la semilla). Los `GET` existentes no cambian de
  permiso.

## Archivos a tocar
```
src/main/resources/db/migration/V17__catalog_admin.sql          (nuevo)
src/main/java/com/bellidental/
  persistence/entity/Treatment.java                  (modif.) + `Long specialtyId` (columna specialty_id), @Audited a nivel de clase
  persistence/entity/Specialty.java                  (modif.) @Audited a nivel de clase
  persistence/repository/TreatmentRepository.java    (modif.) + findAllByOrderByCodeAsc(); + existsByCodeIgnoreCase(String)
  persistence/repository/SpecialtyRepository.java    (modif.) + findAllByOrderByName(); + existsByNameIgnoreCase(String);
                                                     + existsByNameIgnoreCaseAndIdNot(String, Long);
                                                     + consulta nativa countFutureActiveAppointments(Long specialtyId)
  treatments/api/TreatmentController.java            (modif.) + POST /treatments, PUT /treatments/{id},
                                                     POST /treatments/{id}/deactivate|activate, GET /treatments/manage;
                                                     GET /treatments gana `specialtyId` opcional
  treatments/api/dto/
    TreatmentResponse.java                           (modif.) añadir al FINAL `Long specialtyId, boolean active`
    CreateTreatmentRequest.java                      (nuevo) code, name, description, basePrice, specialtyId
    UpdateTreatmentRequest.java                      (nuevo) name, description, basePrice, specialtyId
  treatments/application/TreatmentService.java       (modif.) create, update, setActive, listAll; filtro por especialidad
  professionals/SpecialtyController.java             (modif.) + POST /specialties, PUT /specialties/{id},
                                                     POST /specialties/{id}/deactivate|activate, GET /specialties/manage
  professionals/SpecialtyResponse.java               (modif.) añadir al FINAL `boolean active` (y en `from`)
  professionals/CreateSpecialtyRequest.java          (nuevo) name, defaultDurationMinutes
  professionals/UpdateSpecialtyRequest.java          (nuevo) name, defaultDurationMinutes
  professionals/SpecialtyService.java                (nuevo) create, update, setActive
  config/BotProperties.java                          (solo lectura: `urgentSpecialtyId()`)
  audit/application/AuditService.java                (modif.) en AUDITED_ENTITIES: "treatments" → Treatment.class,
                                                     "specialties" → Specialty.class
src/test/java/com/bellidental/treatments/CatalogAdminIT.java     (nuevo)
docs/IMPLEMENTATION-PLAN.md                          (ya actualizado por el orquestador; no tocar)
```
`new TreatmentResponse(` solo aparece en `TreatmentService.toTreatmentResponse`; `SpecialtyResponse.from` es el único
constructor de `SpecialtyResponse` (verificado). Añadir campos al final no rompe el JSON del frontend.

## Criterios de aceptación
Respuestas con `ApiResponse`; errores con `BusinessException` (409), `InvalidInputException` (400),
`ResourceNotFoundException` (404), como el resto del proyecto.

1. **V17** (`V17__catalog_admin.sql`):
   - `ALTER TABLE treatment_catalog ADD COLUMN specialty_id bigint REFERENCES specialties(specialty_id);`
     e índice `idx_treatment_catalog_specialty_id`.
   - `treatment_catalog_aud` y `specialties_aud`, con el mismo estilo que V16: una columna por campo mapeado de la
     entidad, mismo tipo SQL que la original, **sin NOT NULL/DEFAULT/CHECK/FK/UNIQUE**, más
     `rev bigint NOT NULL REFERENCES revision_info(revision_id)`, `revtype smallint` y `PRIMARY KEY (<id>, rev)`.
     `treatment_catalog_aud`: `treatment_id bigint, code varchar(30), name varchar(150), description text,
     base_price decimal(10,2), active boolean, specialty_id bigint`. `specialties_aud`: `specialty_id bigint,
     name varchar(100), default_duration_minutes int, active boolean`.
   - `ddl-auto: validate` valida las `*_aud` contra las entidades al arrancar: si falla, se corrige V17 (nunca se
     desactiva la validación).
2. **Tratamientos — lectura**:
   - `GET /api/v1/treatments?query=&specialtyId=` (RECORD_READ, sin cambios de permiso): sigue devolviendo solo activos.
     Si llega `specialtyId`, devuelve los activos con esa especialidad **más** los de `specialty_id IS NULL`
     (filtro en Java sobre el resultado; el catálogo es pequeño). Sin `specialtyId` el comportamiento es el actual.
   - `GET /api/v1/treatments/manage` (`hasRole('ADMIN')`): todo el catálogo, activos e inactivos, orden por código.
   - `TreatmentResponse` incluye `specialtyId` y `active`.
3. **`POST /api/v1/treatments`** (ADMIN) → 201 `TreatmentResponse`:
   - `code` `@NotBlank @Size(max = 30)`, se guarda con `trim().toUpperCase()`; `existsByCodeIgnoreCase` → 409
     `"El código '<code>' ya existe."`.
   - `name` `@NotBlank @Size(max = 150)` (trim); `description` opcional; `basePrice` `@NotNull @DecimalMin("0.00")
     @Digits(integer = 8, fraction = 2)`.
   - `specialtyId` opcional: si llega debe existir y estar activa; si no → 400 `"Especialidad inexistente o inactiva."`.
   - Se crea `active = true`.
4. **`PUT /api/v1/treatments/{id}`** (ADMIN) → 200: reemplaza `name`, `description`, `basePrice`, `specialtyId`
   (con `specialtyId` ausente/null se **quita** la especialidad). Mismas validaciones. 404 si no existe. El `code` no
   se envía ni se cambia.
5. **`POST /api/v1/treatments/{id}/deactivate` y `/activate`** (ADMIN) → 200 `TreatmentResponse`. 404 si no existe.
   Idempotente (desactivar uno ya inactivo no falla). Un tratamiento inactivo no aparece en `GET /treatments`, el bot
   deja de listarlo (ya filtra por activos) y `recordTreatment` ya rechaza inactivos (400, existente).
6. **Especialidades — lectura**: `GET /api/v1/specialties` sigue igual (solo activas, PROFESSIONAL_READ).
   `GET /api/v1/specialties/manage` (ADMIN): todas, activas e inactivas, por nombre. `SpecialtyResponse` incluye `active`.
7. **`POST /api/v1/specialties`** (ADMIN) → 201: `name` `@NotBlank @Size(max = 100)` (trim); duplicado
   (`existsByNameIgnoreCase`) → 409 `"La especialidad '<name>' ya existe."`. `defaultDurationMinutes` `@NotNull`,
   múltiplo de 15 entre 15 y 240 (si no → 400 `"La duración debe ser múltiplo de 15 minutos, entre 15 y 240."`).
   Se crea activa.
8. **`PUT /api/v1/specialties/{id}`** (ADMIN) → 200: cambia `name` (duplicado de **otra** fila → 409) y
   `defaultDurationMinutes` (misma regla). Solo afecta la disponibilidad futura. 404 si no existe.
9. **`POST /api/v1/specialties/{id}/deactivate`** (ADMIN) → 200. 404 si no existe. Se rechaza con 409 si:
   - es la especialidad de urgencias del bot (`BotProperties.urgentSpecialtyId()` igual al id):
     `"Es la especialidad de urgencias del bot; cámbiala en la configuración antes."`;
   - algún profesional activo la tiene activa (`ProfessionalSpecialtyRepository.findBySpecialtyIdAndActiveTrue`
     devuelve alguna fila cuyo profesional esté activo y no borrado; si no es fácil filtrar por profesional activo, basta
     con que la lista no esté vacía): `"Hay profesionales con esta especialidad; quítasela antes."`;
   - hay citas futuras `PENDING`/`CONFIRMED` con esa especialidad
     (`countFutureActiveAppointments`, consulta nativa sobre `appointments` con `start_at > now()`):
     `"Hay citas futuras con esta especialidad."`.
   `POST /specialties/{id}/activate` → 200 sin reglas extra.
9b. Al desactivar una especialidad, los tratamientos que la referencian conservan su `specialty_id` (no se tocan).
10. **Auditoría**: `Treatment` y `Specialty` llevan `@Audited` a nivel de clase; `GET /api/v1/audit/treatments/{id}` y
    `GET /api/v1/audit/specialties/{id}` funcionan para ADMIN con el mecanismo existente (solo se agregan las dos
    entradas al mapa de `AuditService`).
11. Controladores: `@PreAuthorize("hasRole('ADMIN')")` en todos los endpoints nuevos. SECRETARIA y ODONTOLOGO → 403.
    Sin `log.*` innecesarios. Respetar `.agents/skills/architecture/SKILL.md` y `clean-code/SKILL.md`: `treatments`
    puede usar `SpecialtyRepository` (capa `persistence`), no clases de `professionals`.

## Tests requeridos
**Integration** (`CatalogAdminIT`, 3 casos; login con helper `loginAs` como `UserManagementIT`; códigos y nombres con sufijo
aleatorio porque la base es compartida entre IT; sin unit tests):
- `treatmentLifecycle_asAdmin_createsUpdatesFiltersAndDeactivates`: director crea tratamiento con `specialtyId = 2`
  (Endodoncia) y precio 50.00 → 201 con `active = true`; `GET /treatments?specialtyId=1` NO lo lista y
  `GET /treatments?specialtyId=2` SÍ; `PUT` con `basePrice` 55.00 → 200; `POST deactivate` → ya no sale en
  `GET /treatments` pero sí en `GET /treatments/manage` con `active = false`; el mismo `code` repetido → 409;
  como SECRETARIA (`secretaria@bellidental.local`) `POST /treatments` → 403 y como odontóloga
  (`odontologa@bellidental.local`) también 403.
- `specialtyLifecycle_asAdmin_rejectsDuplicatesAndInUseDeactivation`: director crea especialidad nueva → 201; nombre
  repetido → 409; duración 20 → 400; desactivarla (sin profesionales ni citas) → 200 y desaparece de
  `GET /specialties`; desactivar la especialidad 1 (Odontología general, tiene profesionales) → 409.
- `auditTrail_afterPriceChange_listsOldAndNewPrice`: crear tratamiento, cambiar `basePrice` con `PUT`;
  `GET /audit/treatments/{id}` como director → ≥ 2 entradas, la última `type = MOD` con un cambio
  `field = "basePrice"`. Como secretaria → 403.

## Decisiones (confirmadas por el usuario el 2026-10-21)
- **D-27**: CRUD por API en lugar de datos quemados; sin tocar el modelo salvo la columna opcional
  `treatment_catalog.specialty_id`; solo ADMIN gestiona el catálogo; se audita con Envers.
- Fuera de alcance: importación masiva por CSV, indicador «precio público» para el bot, relación muchos a muchos
  tratamiento ↔ especialidad.

## Resultado

**Archivos creados**
- `src/main/resources/db/migration/V17__catalog_admin.sql` (columna `specialty_id`, índice, `treatment_catalog_aud`, `specialties_aud`).
- `treatments/api/dto/CreateTreatmentRequest.java`, `treatments/api/dto/UpdateTreatmentRequest.java`.
- `professionals/SpecialtyService.java`, `professionals/CreateSpecialtyRequest.java`, `professionals/UpdateSpecialtyRequest.java`.
- `src/test/java/com/bellidental/treatments/CatalogAdminIT.java` (3 casos).

**Archivos modificados**
- `persistence/entity/Treatment.java` (`@Audited`, `Long specialtyId`), `persistence/entity/Specialty.java` (`@Audited`).
- `persistence/repository/TreatmentRepository.java` (`findAllByOrderByCodeAsc`, `existsByCodeIgnoreCase`).
- `persistence/repository/SpecialtyRepository.java` (`findAllByOrderByName`, `existsByNameIgnoreCase`, `existsByNameIgnoreCaseAndIdNot`, `countFutureActiveAppointments`).
- `treatments/api/TreatmentController.java`, `treatments/application/TreatmentService.java`, `treatments/api/dto/TreatmentResponse.java`.
- `professionals/SpecialtyController.java`, `professionals/SpecialtyResponse.java`.
- `audit/application/AuditService.java`, `audit/api/AuditController.java` (ver supuestos).

**Tests (resumen real)**
- Surefire: 103 tests, 0 fallos, 0 errores, 0 omitidos.
- Failsafe: 75 tests, 0 fallos, 0 errores, 0 omitidos (72 existentes + 3 de `CatalogAdminIT`).
- `./mvnw -q clean verify` con JDK 21: exit 0. `Schema-validation` pasa con V17 (no fue necesario corregirla).

**Problemas y supuestos**
- JDK: `JAVA_HOME` apunta a jdk-17 y el `pom.xml` exige Java 21. No hay JDK 21 instalado; para compilar y verificar se usó el JBR 21.0.6 de IntelliJ (`C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.1.1.1\jbr`) solo como `JAVA_HOME` en los comandos Maven, sin cambiar la configuración del sistema ni el proyecto. Sobreescribir `java.version=17` no sirve: `OdontogramService` usa `List.getFirst()` (Java 21).
- Auditoría: la tarea solo pedía añadir las dos entradas al mapa, pero `AuditController` y `AuditService.findHistory` recibían `UUID` y `treatment_catalog`/`specialties` tienen id `bigint`. Se cambió el parámetro `id` a `String` y se convierte según el tipo real del `@Id` de la entidad (`Long` o `UUID`); un id inválido responde 400. Sin este cambio `GET /audit/treatments/{id}` no funcionaba.
- `SpecialtyController` ahora delega en `SpecialtyService` también la lectura de `GET /specialties` (mismo resultado).
- Al desactivar una especialidad, el chequeo de profesionales considera solo los profesionales activos y no borrados (`findBySpecialtyIdAndActiveTrue` filtrado en Java).
- `create` de especialidad valida la duración antes de comprobar el nombre duplicado.
- Los cambios de `docs/IMPLEMENTATION-PLAN.md`, `012`, `019` y `020` que aparecen en `git status` ya estaban antes de esta tarea; no se tocaron. `V1`–`V16` y `ddl-auto` no se modificaron.
- No se hizo git add, commit, push ni cambio de rama.

## Revisión
Estado: APROBADO

- `./mvnw -q clean verify` (JDK 21 del JBR de IntelliJ) exit 0, repetido por el revisor. Surefire 103/0/0/0, failsafe 75/0/0/0 (incluye CatalogAdminIT 3).
- V17 coincide con las entidades (`treatment_catalog.specialty_id` y las dos `*_aud`); `ddl-auto: validate` pasa.
- Todos los endpoints nuevos llevan `hasRole('ADMIN')`; sin `DELETE`; el código del tratamiento no se edita; la desactivación de especialidades rechaza urgencias del bot, profesionales activos y citas futuras.
- El cambio de `AuditController`/`AuditService` (id como `String`, convertido a Long o UUID según la entidad) es necesario porque estas dos tablas usan id `bigint`; los `GET /audit/...` existentes siguen igual.
- Nota de entorno: el equipo no tiene JDK 21 en `JAVA_HOME` (apunta a 17); se compiló con el JBR de IntelliJ.
