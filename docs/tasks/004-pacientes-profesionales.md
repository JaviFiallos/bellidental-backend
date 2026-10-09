# 004 — Pacientes y profesionales (API)

## Objetivo
Dejar listos los módulos existentes `patients` y `professionals` para recepción, agenda y bot:
autorización por permisos, búsqueda de pacientes, validaciones de Ecuador, sin borrado y con interfaces
públicas para otros módulos (RF06 parcial, RF07 base; plan §4.1). Sin migraciones nuevas.

## Archivos a tocar
- `patients/`: `PatientController`, `PatientService`, DTOs; nuevo `PatientDirectory` (interfaz pública) y su implementación.
- `professionals/`: `ProfessionalController`, `ProfessionalService`, DTOs; nuevo `ProfessionalDirectory`;
  nuevo `SpecialtyController` (o endpoint en el mismo controlador) para listar especialidades.
- `persistence/entity/Patient.java`, `Professional.java`: fechas a `OffsetDateTime`.
- `persistence/repository/PatientRepository.java`, `ProfessionalRepository.java`, `ProfessionalSpecialtyRepository.java`.
- `shared/validation/`: `PhoneNumberNormalizer`, `EcuadorianIdValidator` (nuevos).
- `shared/enums/Gender.java`, `shared/enums/Specialty.java`: borrar si quedan sin uso (el esquema usa `sex` y la tabla `specialties`).
- `application.yml`: `application.clinic.zone: America/Guayaquil`.
- Tests unitarios en `src/test/java/com/bellidental/patients/`, `professionals/` y `shared/validation/`.

## Criterios de aceptación
**Autorización**
1. Todos los endpoints usan `hasAuthority('<PERMISO>')`, no `hasRole`:
   pacientes PATIENT_CREATE / PATIENT_READ / PATIENT_UPDATE; profesionales PROFESSIONAL_READ / PROFESSIONAL_MANAGE.

**Pacientes**
2. `POST /api/v1/patients` (PATIENT_CREATE): `documentType`, `documentNumber`, `firstName`, `lastName`, `birthDate`
   y `sex` obligatorios; `sex` solo `M` o `F`. Documento duplicado (tipo + número) → 409.
3. Con `documentType = CI`, la cédula se valida (10 dígitos, provincia 01–24 o 30, dígito verificador módulo 10);
   inválida → 400. Otros tipos solo validan longitud.
4. Teléfonos (`phoneE164`, `emergencyContactPhoneE164`) se normalizan a E.164 sin "+":
   `0987654321` → `593987654321`; `+593 98 765 4321` → `593987654321`; ya normalizado se respeta;
   formato no reconocible → 400. Puede ser nulo.
5. `PUT /api/v1/patients/{id}` (PATIENT_UPDATE): mismas validaciones; `adminNotes` editable.
6. `GET /api/v1/patients?query=&page=&size=` (PATIENT_READ), paginado (máx. 50), solo activos y no borrados:
   - solo dígitos → coincidencia exacta de cédula **o** prefijo de teléfono (normalizado);
   - texto → nombre o apellido con `ILIKE` (el índice trigram llega en V7); mínimo 2 caracteres, si no → 400.
   Reemplaza al `GET` que devuelve todos los pacientes.
7. `GET /api/v1/patients/{id}` (PATIENT_READ): identificación + `age` calculada a hoy en `application.clinic.zone`.
   Nunca datos clínicos.
8. Sin borrado: se elimina `DELETE /patients/{id}`; se añaden `POST /{id}/deactivate` y `POST /{id}/activate`
   (PATIENT_UPDATE) que cambian `active`.
9. `PatientDirectory` (interfaz pública del módulo): `findById(UUID)`, `findActiveByPhone(String phoneE164)` →
   lista (el teléfono no es único), `findByDocument(DocumentType, String)`. Devuelve un record propio del módulo,
   no la entidad.

**Profesionales**
10. `GET /api/v1/professionals` (PROFESSIONAL_READ): activos, con especialidades activas (id, nombre, duración) y `colorTag`.
    `GET /{id}` igual para uno.
11. `PUT /api/v1/professionals/{id}` (PROFESSIONAL_MANAGE): datos, `colorTag` (`^#[0-9A-Fa-f]{6}$`, si no → 400),
    `googleCalendarId`, `phoneE164` (normalizado) y lista de `specialtyIds`. Las especialidades se sincronizan:
    nuevas → fila activa; retiradas → `active=false` (no se borran, la agenda las referenciará); reincorporadas → `active=true`.
12. `POST /api/v1/professionals` (PROFESSIONAL_MANAGE): igual que PUT; `licenseNumber` obligatorio y único (409);
    `userId` opcional para vincular una cuenta existente (si el usuario no existe o ya tiene profesional → 409).
13. Sin borrado: se elimina `DELETE /professionals/{id}`; `POST /{id}/deactivate` y `/activate` (PROFESSIONAL_MANAGE).
14. `GET /api/v1/specialties` (PROFESSIONAL_READ): especialidades activas.
15. `ProfessionalDirectory` (interfaz pública): `findById`, `findActive()`, `hasActiveSpecialty(professionalId, specialtyId)`,
    `findByUserId(UUID)`. Devuelve records propios, no entidades.

**General**
16. Entidades `Patient` y `Professional` y sus DTOs con `OffsetDateTime` (como `User` en la tarea 003).
17. Errores con el formato de `GlobalExceptionHandler`: 400 validación, 404 no existe, 409 duplicado.
18. No se tocan migraciones, semillas ni el módulo de agenda. Los horarios de profesionales van en la tarea 006.

## Tests requeridos
- Unit `PhoneNumberNormalizerTest`: local con 0, con "+593" y espacios, ya normalizado, nulo, inválido.
- Unit `EcuadorianIdValidatorTest`: cédula válida, dígito verificador erróneo, provincia inválida, longitud distinta de 10.
- Unit `PatientServiceTest`: documento duplicado → conflicto; búsqueda decide cédula/teléfono vs nombre; consulta
  < 2 caracteres → error; `deactivate` cambia `active`; edad calculada en la zona configurada.
- Unit `ProfessionalServiceTest`: sincronización de especialidades (alta, baja lógica, reactivación); color inválido;
  `userId` ya vinculado → conflicto.
- Integration: ninguno (módulo no crítico).

## Resultado
**Archivos modificados:**
- `PatientService`, `ProfessionalService`, `PatientRepository`, `ProfessionalRepository`: Lógica refactorizada para comprobaciones exactas y excluyentes de borrados.
- DTOs (`*Request`, `*Response`): limpieza general de Full Qualified Names por importaciones estáticas y convencionales.
- `GlobalExceptionHandler`: Agregado `@ExceptionHandler(InvalidInputException.class)` para retornar código 400 y removido el antiguo manejador `IllegalArgumentException`.
- `PhoneNumberNormalizer`: Ahora arroja `InvalidInputException` tras refactorizar `IllegalArgumentException`.
- Tests actualizados `PatientServiceTest`, `ProfessionalServiceTest` y `PhoneNumberNormalizerTest` cubriendo nuevos flujos (duplicidad de licencias, edades y especialidades reactivadas).

**Resultado de los tests:**
- Surefire: 30 tests ejecutados, 0 fallos, 0 errores.
- Failsafe: 11 tests ejecutados, 0 fallos, 0 errores.

**Soluciones implementadas:**
- `existsByLicenseNumber` y `existsByDocumentTypeAndDocumentNumber` chequean duplicados íntegros, incluso contra los inactivos/borrados lógicamente.
- Las búsquedas de teléfonos con prefijo de 0 ahora llaman al método utilitario `toPhonePrefix` para normalizarlas en `593`.
- Se corrigieron todos los warnings de nombres completamente calificados mediante limpieza de imports a nivel de controladores, repositorios, servicios y DTOs (verificado mediante la regex).

## Revisión
**Estado: CORRECCIONES** (revisión 1, Claude)

Verificado por Claude con `./mvnw clean verify`: surefire 26/26 (`PatientServiceTest` 6, `ProfessionalServiceTest` 2,
`PhoneNumberNormalizerTest` 5, `EcuadorianIdValidatorTest` 6, más 7 de la tarea 003); failsafe 11/11 sin regresiones.
Cumplidos: permisos con `hasAuthority`, sin `DELETE`, activate/deactivate, `/specialties`, sincronización de
especialidades sin borrar, directorios con records propios, `OffsetDateTime`, `application.clinic.zone`, enums borrados.

Corregir:
1. **Duplicado de documento (criterio 2):** se comprueba solo `documentNumber` y excluyendo borrados, pero el UNIQUE
   de la BD es `(document_type, document_number)` sobre todas las filas. Con un paciente borrado lógicamente la BD lanza
   la violación → 500. Usar `existsByDocumentTypeAndDocumentNumber` (sin filtrar `deleted_at`) en pacientes y profesionales.
2. **Búsqueda por teléfono (criterio 6):** se busca el prefijo tal como se escribe; `0987…` nunca coincide con
   `593987…`. Si la consulta empieza por `0`, buscar el prefijo `593` + resto; mantener la coincidencia exacta de cédula.
3. **Excepción de validación:** no mapear `IllegalArgumentException` global a 400 (cualquier error interno de una
   librería saldría como 400 con su mensaje). Crear `shared/exception/InvalidInputException` (400), lanzarla en
   validadores y servicios y quitar el handler de `IllegalArgumentException`.
4. **Duplicación:** la validación de cédula está repetida en `create`/`update` de pacientes y profesionales. Un único
   método (p. ej. `DocumentValidator.validate(documentType, documentNumber)` en `shared/validation`).
5. **`active` en `UpdatePatientRequest` y `UpdateProfessionalRequest`:** quitarlo; el estado solo cambia con
   `activate`/`deactivate` (criterios 8 y 13).
6. **`ProfessionalRepository.findAllActive`** no filtra `active = true` (criterio 10: activos). Añadirlo.
7. **Nombres totalmente calificados** en `PatientService`, `ProfessionalService`, `PatientRepository`, controladores y
   DTOs (`org.springframework.data.domain.Page`, `java.time.ZoneId`, `com.bellidental...`). Usar imports
   (regla añadida a clean-code/SKILL.md).
8. Tests: añadir a `PatientServiceTest` el caso de prefijo `0…` → `593…` y a ambos servicios el duplicado por
   (tipo, número) incluyendo borrados.
9. Resultado: incluir también la línea de failsafe.
10. Nombres: la regla de clean-code prohíbe `info`. Renombrar `PatientInfo` → `PatientSummary` y
    `ProfessionalInfo` → `ProfessionalSummary` (y `mapToInfo` → `toSummary`).

---
**Estado: CORRECCIONES** (revisión 2, Claude)

Verificado con `./mvnw clean verify`: surefire 28/28, failsafe 11/11. Resueltas de la revisión 1: 1 (pacientes),
2, 4, 5, 6, 9, 10. El Resultado afirma cosas que no están en el código (3 y 7). Corregir:
1. **`InvalidInputException` no tiene handler** → hoy sale como 500 (cédula inválida, consulta < 2 caracteres).
   Añadir `@ExceptionHandler(InvalidInputException.class)` → 400 en `GlobalExceptionHandler`.
2. **Quitar `handleIllegalArgument`** de `GlobalExceptionHandler` (pendiente desde la revisión 1, punto 3) y hacer que
   `PhoneNumberNormalizer` lance `InvalidInputException` en vez de `IllegalArgumentException`; ajustar su test.
3. **Licencia duplicada:** `license_number` es UNIQUE sobre todas las filas (V2). Usar `existsByLicenseNumber`
   (sin `DeletedAtIsNull`) en `create` y `update`; si no, un profesional borrado provoca 500.
4. **Nombres totalmente calificados siguen ahí** (revisión 1, punto 7): 17 en `ProfessionalService`, 4 en
   `PatientService` (`PageRequest`, `InvalidInputException`, `DocumentValidator`), y en `Create/UpdatePatientRequest`,
   `Create/UpdateProfessionalRequest` (`jakarta...Pattern`, `java.util.List`), `ProfessionalResponse` y `PatientServiceTest`.
   Usar imports. Comprobar con:
   `grep -rnE "(org\.springframework|java\.(util|time)|com\.bellidental|jakarta)\.[a-z.]*[A-Z]" src | grep -vE ":(import|package) "`
   (debe quedar vacío, salvo `AuthServiceTest` de la tarea 003, que no se toca).
5. **Tests requeridos que faltan:** en `PatientServiceTest`, edad calculada en la zona configurada (fijar
   `clinicZone` con `ReflectionTestUtils`); en `ProfessionalServiceTest`, reactivación de una especialidad inactiva
   (caso separado del alta/baja) y duplicado de licencia.
6. `PatientService.search`: el comentario `// solo dígitos` y la variable `q` sobran; extraer
   `toPhonePrefix(String digits)` (nombre claro, sin abreviaturas).
7. Resultado: reemplazarlo describiendo solo lo que realmente está en el código.

---
**Estado: APROBADO** (revisión 3, Claude)

Verificado con `./mvnw clean verify`: surefire 30/30, failsafe 11/11. Resueltos los 7 puntos de la revisión 2:
`InvalidInputException` → 400, sin handler de `IllegalArgumentException`, `existsByLicenseNumber` sin filtrar borrados,
sin nombres calificados (quedan `AuthServiceTest`, fuera de alcance, y `CustomUserDetailsService`, choque real con `User`),
tests de edad, reactivación y licencia duplicada, `toPhonePrefix`.
Corrección mínima de Claude: test de edad renombrado a `findById_bornTenYearsAgoInClinicZone_returnsAgeTen`.
