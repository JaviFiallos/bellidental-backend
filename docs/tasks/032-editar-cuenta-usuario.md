# 032 — El director edita email y nombre de usuario de una cuenta

## Objetivo
El director (ADMIN) ya cambia roles, contraseña temporal y activo, y por API el email (`PUT /api/v1/users/{id}`,
tarea 019). Debe poder cambiar también el **nombre de usuario** (`users.username`, el que se ve en auditoría y en
"recibido por" de los pagos). La actualización es **parcial**: se envía solo lo que cambia.

Sin migraciones, permisos ni endpoints nuevos: se amplía el `PUT /api/v1/users/{id}` existente (D-24b).

## Archivos a tocar
```
src/main/java/com/bellidental/identity/
  api/
    UserController.java              summary de Swagger del PUT: "Cambiar email y/o nombre de usuario"
    dto/UpdateUserRequest.java       {email?, username?}
  application/
    UserService.java                 update parcial; javadoc de la clase corregido
src/test/java/com/bellidental/identity/
  UserManagementIT.java              +2 casos
docs/IMPLEMENTATION-PLAN.md          §4.13 fila del PUT, decisión D-24b, fila 032
```

## Criterios de aceptación
1. `UpdateUserRequest(String email, String username)`: los dos son opcionales (sin `@NotBlank`).
   `email` con `@Email` y `@Size(max = 150)`; `username` con `@Size(max = 60)`; mensajes en español, como hoy.
2. `UserService.update`:
   - `null` = no cambia ese campo.
   - Si los dos son `null` → `InvalidInputException` ("Indique el email o el nombre de usuario a cambiar.") → 400.
   - Si se envía un campo en blanco (`""` o solo espacios) → `InvalidInputException` → 400.
   - El email se normaliza con `normalizeEmail`; el username se recorta (`trim`), sin pasarlo a minúsculas.
   - Si el valor nuevo es igual al actual, no se valida duplicado (no hay error).
   - Si otra cuenta ya usa el email o el username → `BusinessException` con el mismo mensaje que `createAccount`
     ("El email '…' ya está en uso." / "El nombre de usuario '…' ya está en uso.") → 409.
   - Sin duplicación: la comprobación de duplicados y la normalización se extraen a métodos privados que usan
     `createAccount` y `update`.
3. Sigue `requireNotSystem` (`bot_whatsapp` → 409). El ADMIN **sí** puede editar su propia cuenta y la de cualquier
   otro ADMIN (solo email y username; los roles del ADMIN siguen bloqueados en `updateRoles`). Corregir el javadoc
   de `UserService` ("El ADMIN no se crea ni se modifica por API") para que diga: "El ADMIN no se crea por API ni
   cambia sus roles."
4. Cambiar el email no invalida la sesión: el JWT ya emitido sigue sirviendo (el filtro relee por `user_id`) y
   `GET /api/v1/auth/me` devuelve el email nuevo. El siguiente login es con el email nuevo; el antiguo → 401.
   No se cambia nada en `security/` para esto: solo se comprueba en el IT.
5. Respuesta: `200` con `ApiResponse<UserResponse>` ("Usuario actualizado."), igual que hoy.
6. Plan §4.13: la fila del PUT pasa a `| PUT /api/v1/users/{id} | ADMIN | {email?, username?} | Cambia email y/o
   nombre de usuario (parcial: lo que no se envía no cambia; 400 si no se envía nada; 409 si ya lo usa otra cuenta).
   El ADMIN puede editar su propia cuenta |`. Añadir en la tabla de decisiones, tras D-24:
   `| D-24b | El ADMIN edita email y username de cualquier cuenta no del sistema, incluida la suya; actualización
   parcial por el mismo PUT; la sesión sigue válida; la auditoría muestra el username actual (se resuelve por
   user_id) | §4.13, tarea 032 |`. Fila de tareas tras la 031:
   `| 032 | El director edita email y nombre de usuario de una cuenta (D-24b) | — | 019 | UserManagementIT (casos nuevos) |`.
7. No se toca `CreateUserRequest`, `UserResponse`, `security/` ni otros módulos.
8. `./mvnw -q clean verify` pasa.

## Tests requeridos
- **Unit:** ninguno.
- **Integration:** `UserManagementIT`, dos casos nuevos (crear la cuenta con `POST /api/v1/users` como director,
  igual que los casos que ya están en el archivo):
  1. `updateUser_partialFields_changesOnlySentFieldsAndKeepsSession`: login de la cuenta nueva y guardar su token.
     El director envía solo `{username}` → 200, cambia el username y el email queda igual. Luego envía solo
     `{email}` → 200, cambia el email y el username queda igual. Con el token guardado, `/auth/me` → 200 con el email
     nuevo. Login con el email antiguo → 401; con el nuevo → 200.
  2. `updateUser_duplicateOrEmpty_returnsConflictOrBadRequest`: username de otra cuenta (p. ej. `secretaria` de la
     semilla) → 409; cuerpo `{}` → 400.

## Resultado
- **Qué se hizo:**
  - `src/main/java/com/bellidental/identity/api/dto/UpdateUserRequest.java`: Se actualizaron los campos a `{email?, username?}` opcionales (sin `@NotBlank`), con validaciones `@Email` y `@Size(max = 150)` para `email`, y `@Size(max = 60)` para `username`.
  - `src/main/java/com/bellidental/identity/api/UserController.java`: Se actualizó el summary Swagger de la operación PUT a `"Cambiar email y/o nombre de usuario"`.
  - `src/main/java/com/bellidental/identity/application/UserService.java`: Se corrigió el javadoc de la clase indicando que el ADMIN no se crea por API ni cambia sus roles. Se extrajeron métodos privados auxiliares (`normalizeEmail`, `normalizeUsername`, `checkEmailAvailable`, `checkUsernameAvailable`) para reutilización sin duplicación entre `createAccount` y `update`. Se implementó la actualización parcial en `update`, verificando que si ambos campos son nulos o vienen en blanco lance `InvalidInputException` (400), validando duplicados contra la base de datos únicamente si el valor enviado es distinto al actual (`BusinessException` 409), y preservando `requireNotSystem` permitiendo editar la propia cuenta del director.
  - `src/test/java/com/bellidental/identity/UserManagementIT.java`: Se incorporaron los 2 tests de integración requeridos (`updateUser_partialFields_changesOnlySentFieldsAndKeepsSession` y `updateUser_duplicateOrEmpty_returnsConflictOrBadRequest`).
  - `docs/IMPLEMENTATION-PLAN.md`: Se actualizó la fila del PUT en §4.13, se agregó la decisión D-24b y se añadió la tarea 032 a la tabla de tareas.
- **Resultado de los tests:**
  - Surefire: Tests run: 103, Failures: 0, Errors: 0, Skipped: 0
  - Failsafe: Tests run: 99, Failures: 0, Errors: 0, Skipped: 0
- **Problemas encontrados y supuestos tomados:**
  - Ninguno. La suite completa `./mvnw -q clean verify` pasó sin errores ni fallos.

## Revisión

Estado: APROBADO

- Criterios 1–8 cumplidos. Update parcial correcto: `null` no cambia, blanco → 400, igual al actual no valida
  duplicado, duplicado → 409. Duplicados y normalización extraídos y reutilizados por `createAccount`.
- `requireNotSystem` se mantiene; el ADMIN puede editar su cuenta; javadoc corregido.
- IT cubre parcial en ambos sentidos, sesión viva con `/me`, login antiguo 401 / nuevo 200, duplicado 409 y `{}` 400.
- Totales: surefire 103/0, failsafe 99/0.
- Nota menor (sin corrección): `request == null` en `update` es redundante (`@RequestBody` es obligatorio).
