# 019 — Gestión de usuarios por el director (ADMIN)

## Objetivo
**Requerimiento nuevo del usuario**: «el doctor (director, ADMIN) puede manejar usuarios: crearlos, modificarlos,
etc.». Contradice `MVP-SCOPE.md` («sin UI de usuarios») y D-13 (alta de cuentas posterior al MVP). El usuario lo
pidió de forma explícita, así que se implementa con la decisión **provisional D-24**, que reemplaza a D-13. Esta
tarea amplía el módulo `identity` (ya tiene `api`, `api/dto`, `application`; se añade `domain`; no se reorganiza).

Base: endpoints estipulados en plan §4.13, más `POST /api/v1/users` para crear y `PUT /users/{id}/roles`. Sin
cambios de esquema (no hay `must_change_password`).

## Archivos a tocar
```
src/main/java/com/bellidental/
  identity/
    api/UserController.java                   (modif.) añadir POST /users, PUT /users/{id}/roles,
                                              POST /users/{id}/reset-password, POST /users/{id}/deactivate,
                                              POST /users/{id}/activate; quitar DELETE /users/{id}
    api/ProfessionalAccessController.java     (nuevo) POST /api/v1/professionals/{id}/access
    api/AuthController.java                   (modif.) añadir PUT /api/v1/auth/password
    api/dto/
      CreateUserRequest.java                  (nuevo) email, username (opcional), temporaryPassword, roles (Set<String>)
      UpdateUserRolesRequest.java             (nuevo) roles (Set<String>, @NotEmpty)
      ResetPasswordRequest.java               (nuevo) temporaryPassword
      ChangePasswordRequest.java              (nuevo) currentPassword, newPassword
      GrantAccessRequest.java                 (nuevo) email, temporaryPassword
      UpdateUserRequest.java                  (modif.) quitar `password` y `active`; queda solo `email`
    application/
      UserService.java                        (modif.) create, updateRoles, resetPassword, setActive; quitar delete
      ProfessionalAccessService.java          (nuevo) grantAccess
      AuthService.java                        (modif.) changePassword
    domain/PasswordRules.java                 (nuevo) pura
  persistence/repository/UserRepository.java  (modif.) añadir existsByEmail(String), existsByUsername(String)
src/test/java/com/bellidental/identity/
  domain/PasswordRulesTest.java               (nuevo)
  UserManagementIT.java                       (nuevo)
docs/MVP-SCOPE.md                             quitar «UI de usuarios/permisos» de «Fuera del MVP» (línea 55) y la línea
                                              59 «Los módulos existentes de creación de usuarios…»; en RNF01 (línea 45)
                                              sustituir «sin UI de usuarios ni permisos» por «el ADMIN gestiona usuarios
                                              (D-24); sin UI de permisos»
docs/IMPLEMENTATION-PLAN.md                   §4.13 retitular «Gestión de usuarios — tarea 019 (D-24)» y poner la tabla
                                              con los endpoints reales de esta tarea; §6: en la fila D-13 escribir
                                              «Reemplazada por D-24»; añadir D-24 en «Pendientes» (provisional)
```
`RoleRepository.findByCode`, `UserRepository.findByIdAndDeletedAtIsNull` y
`ProfessionalRepository.findByIdAndDeletedAtIsNull` ya existen. Nadie más usa `UpdateUserRequest` ni
`DELETE /users` (verificado en `src/test`).

## Criterios de aceptación
Todos los endpoints nuevos llevan `@PreAuthorize("hasRole('ADMIN')")`, salvo `PUT /auth/password`
(`@PreAuthorize("isAuthenticated()")`). Respuestas con `ApiResponse` como el resto de `UserController`.
`CurrentUser` es un bean: se inyecta y se llama `currentUser.userId()` (no es estático).

0. **Unicidad**: `users.username` y `users.email` tienen `UNIQUE` en toda la tabla (también filas con
   `deleted_at`). Por eso los chequeos usan los métodos nuevos `existsByEmail` / `existsByUsername` (sin filtro de
   `deleted_at`), también en `update`. El email se guarda con `trim().toLowerCase()`.
1. `POST /api/v1/users` → 201 `UserResponse`. `email` `@NotBlank @Email @Size(max = 150)`; si existe → 409
   `BusinessException("El email '<email>' ya está en uso.")`. `username` opcional: si falta o está en blanco,
   `PasswordRules.usernameFromEmail(email)`; si existe → 409 `"El nombre de usuario '<username>' ya está en uso."`.
   `temporaryPassword` `@NotBlank` y `PasswordRules.validate` (400). Hash con el `PasswordEncoder` existente.
   `roles` `@NotEmpty`; cada código se valida así: `"ADMIN"` → 400 `InvalidInputException("Rol no permitido:
   ADMIN.")`; no existe en `RoleRepository.findByCode` → 400 `"Rol inexistente: <código>."`; permitidos solo
   `ODONTOLOGO` y `SECRETARIA`. `active = true`, `isSystem = false`.
   Orden de guardado: construir el `User` sin roles, `userRepository.saveAndFlush(user)` (para tener id), luego
   añadir a `user.getUserRoles()` un `UserRole` por rol con `id = new UserRoleId(user.getId(), role.getId())`,
   `user` y `role`, y guardar.
2. `PUT /api/v1/users/{id}/roles` → 200 `UserResponse`. 404 si no existe. Usuario `isSystem` → 409 `"Los usuarios
   del sistema no se pueden modificar."`. Si el usuario objetivo tiene el rol ADMIN → 409 `"No se modifican los
   roles de un administrador."`. Roles nuevos con las mismas reglas que en 1. **No** vaciar la colección y volver a
   añadir (Hibernate inserta antes de borrar y choca la PK): quitar de `userRoles` solo los roles que ya no están y
   añadir solo los que faltan.
3. `POST /api/v1/users/{id}/reset-password` `{temporaryPassword}` → 200 (`ApiResponse<Void>`), reemplaza el hash
   (misma validación). 404 si no existe; `isSystem` → 409 (mismo mensaje).
4. `POST /api/v1/users/{id}/deactivate` → `active = false`; `POST /api/v1/users/{id}/activate` → `active = true`;
   ambos 200 `UserResponse`. Si `id` es `currentUser.userId()` → 409 `"No puedes desactivar tu propia cuenta."`
   (solo en deactivate); `isSystem` → 409; 404 si no existe. Se eliminan `DELETE /users/{id}` y
   `UserService.delete`. El login de un usuario inactivo ya lo rechaza `AuthService` (401).
5. `PUT /api/v1/users/{id}` queda solo para cambiar `email` (normalizado; 409 si otro usuario ya lo usa).
6. `POST /api/v1/professionals/{id}/access` `{email, temporaryPassword}` → 201 `UserResponse`: en una transacción,
   crea el usuario con rol ODONTOLOGO y username = `PasswordRules.usernameFromEmail(email)` (mismas validaciones y
   pasos de 1) y hace `professional.setUser(user)`. 404 si `ProfessionalRepository.findByIdAndDeletedAtIsNull`
   está vacío; 409 `"El profesional ya tiene una cuenta de acceso."` si `professional.getUser() != null`; 409 si el
   email o el username ya existen.
7. `PUT /api/v1/auth/password` `{currentPassword, newPassword}` → 200 (`ApiResponse<Void>`): cambia la contraseña
   del usuario autenticado. `currentPassword` no coincide (`passwordEncoder.matches`) → lanzar
   `BadCredentialsException("La contraseña actual no es correcta.")` (el handler ya responde 401). `newPassword`
   con `PasswordRules.validate` y distinta de la actual (si no → 400 `InvalidInputException("La nueva contraseña
   debe ser distinta de la actual.")`).
8. **Seguridad**: no añadir `log.*` con contraseñas, hashes ni requests. Los records con contraseña
   (`CreateUserRequest`, `ResetPasswordRequest`, `ChangePasswordRequest`, `GrantAccessRequest`) sobreescriben
   `toString()` para devolver `"<NombreDelRecord>[redacted]"`.
9. `PasswordRules` (pura, sin Spring):
   - `static void validate(String password)`: `null` o `length() < 10` → `InvalidInputException("La contraseña debe
     tener al menos 10 caracteres.")`;
   - `static String usernameFromEmail(String email)`: parte antes de `@`, en minúsculas, quitando todo carácter
     fuera de `[a-z0-9._-]`, máximo 60 caracteres.

## Tests requeridos
**Unit** (`PasswordRulesTest`, 3 casos):
- `validate_shorterThanTen_throwsInvalidInput`
- `validate_tenOrMoreCharacters_passes`
- `usernameFromEmail_mixedCase_returnsLowercaseLocalPart` (`"Ana.Perez@X.com"` → `"ana.perez"`)

**Integration** (`UserManagementIT`, 3 casos). Login con un helper `loginAs(email, password)` como el de
`EncounterFlowIT` (la contraseña de la semilla es `Cambiar.2026!`). Cada caso usa emails con sufijo aleatorio
(`"sec-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local"`): la base es compartida entre IT.
- `createUser_asAdmin_canLoginAndChangeOwnPassword`: director crea SECRETARIA con contraseña temporal → 201 con
  `roles = [SECRETARIA]`; login con ese email → 200; `PUT /auth/password` con la actual correcta → 200 y login con
  la nueva → 200; con actual incorrecta → 401.
- `createUser_withAdminRole_returnsBadRequest`: director crea con `roles = ["ADMIN"]` → 400; como SECRETARIA de la
  semilla, `POST /users` → 403.
- `deactivateUser_blocksLoginAndSelfDeactivationIsRejected`: director crea un usuario y lo desactiva → login de
  ese usuario 401; `deactivate` del propio director (`11111111-1111-1111-1111-111111111111`) → 409; `activate` del
  usuario → su login vuelve a dar 200.

## Decisiones provisionales (confirmar con el usuario)
**D-24 (provisional, a confirmar mañana):**
- Solo ADMIN gestiona usuarios. Puede crear **ODONTOLOGO y SECRETARIA**; **no** puede crear ADMIN por API (los
  administradores siguen por semilla/migración). Cambia lo estipulado en §4.13 (allí SECRETARIA tampoco se creaba
  por API).
- Cambiar roles de usuarios existentes: solo entre ODONTOLOGO/SECRETARIA; no sobre otro ADMIN.
- Enlazar profesional existente a una cuenta: `POST /professionals/{id}/access` (rol ODONTOLOGO).
- Reset de contraseña por ADMIN con contraseña temporal; **sin** cambio forzado en el primer login (requeriría
  columna `must_change_password`; queda como recomendación).
- Desactivar/reactivar reemplaza al borrado lógico por API; no se puede desactivar a sí mismo ni tocar `is_system`.
- Contraseña mínima: 10 caracteres.
- Desactivar o cambiar roles tiene efecto inmediato: `JwtAuthenticationFilter` ya relee el usuario y sus roles en
  cada petición (no hay que tocarlo).

## Resultado

**Archivos creados**
- `identity/domain/PasswordRules.java` (validate: mínimo 10; usernameFromEmail).
- `identity/api/ProfessionalAccessController.java`, `identity/application/ProfessionalAccessService.java`.
- DTOs en `identity/api/dto/`: CreateUserRequest, UpdateUserRolesRequest, ResetPasswordRequest, ChangePasswordRequest, GrantAccessRequest (todos con `toString` redactado).
- Tests: `src/test/java/com/bellidental/identity/domain/PasswordRulesTest.java` (3), `src/test/java/com/bellidental/identity/UserManagementIT.java` (3).

**Archivos modificados**
- `identity/api/UserController.java`: POST, PUT `/{id}/roles`, reset-password, deactivate, activate; se quita DELETE.
- `identity/api/AuthController.java`: `PUT /api/v1/auth/password` (`isAuthenticated()`).
- `identity/application/UserService.java`: create/createAccount, updateRoles (quita y añade solo lo necesario), resetPassword, deactivate/activate, update solo email; se quita delete.
- `identity/application/AuthService.java`: changePassword (BadCredentials → 401 si la actual no coincide).
- `identity/api/dto/UpdateUserRequest.java`: queda solo `email` (`@NotBlank`).
- `persistence/repository/UserRepository.java`: `existsByEmail`, `existsByUsername`.
- `docs/MVP-SCOPE.md` (líneas 45, 55, 59), `docs/IMPLEMENTATION-PLAN.md` (§4.13 retitulado con tabla real; D-13 reemplazada; D-24 en Pendientes).

**Tests (resumen real)**
- Surefire: 103 tests, 0 fallos, 0 errores, 0 omitidos (PasswordRulesTest 3/3).
- Failsafe: 72 tests, 0 fallos, 0 errores, 0 omitidos (UserManagementIT 3/3).
- `./mvnw -q clean verify`: exit 0.

**Problemas y supuestos**
- Un import de `PreAuthorize` faltaba en `AuthController` y el primer compilado falló; corregido antes del resultado final.
- `usernameFromEmail` puede devolver cadena vacía si la parte local del email no tiene caracteres válidos; no se añadió validación extra.
- `createAccount` se reutiliza para el alta de acceso de profesionales (username = parte local del email, rol ODONTOLOGO).
- No se hizo commit.

## Revisión
Estado: APROBADO

- `./mvnw -q clean verify` exit 0. Surefire 103/0/0/0, failsafe 72/0/0/0 (incluye PasswordRulesTest 3 y
  UserManagementIT 3).
- Revisión de seguridad:
  - todos los endpoints nuevos llevan `hasRole('ADMIN')`; `PUT /auth/password` lleva `isAuthenticated()` y exige
    la contraseña actual (401);
  - ADMIN no se crea ni se asigna (400), y sus roles no se modifican (409); no se puede autodesactivar (409);
  - la unicidad usa `existsByEmail`/`existsByUsername`, que incluyen las filas con borrado lógico; el email se
    normaliza;
  - el cambio de roles quita y añade solo la diferencia, sin colisión de PK;
  - sin `log.*` en identity; `UserResponse` no expone el hash; los requests con contraseña redactan `toString()`.
- Correcciones mínimas hechas por el revisor (`UserService`):
  1. `update` (cambio de email) ahora también rechaza usuarios `is_system` (409), igual que el resto de operaciones.
  2. Si el username derivado del email queda vacío (parte local sin caracteres válidos) → 400 en vez de guardar `""`.
- Frontend (`../bellidental-frontend`): no usa `/users` (ni `DELETE /users/{id}` ni `password`/`active` de
  `UpdateUserRequest`); quitarlos no rompe nada.
- El archivo de la tarea quedó coherente: es la versión revisada más el Resultado.
