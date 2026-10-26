# 023 — Cambiar contraseña: contraseña actual incorrecta → 400 (G-13 del frontend)

## Objetivo
`PUT /api/v1/auth/password` responde **401** cuando la contraseña actual no coincide (lanza
`BadCredentialsException`). El 401 es también la respuesta de «sesión vencida o token inválido», así que el frontend
no puede distinguir los dos casos: si el token vence justo al cambiar la contraseña, el usuario ve «La contraseña
actual no es correcta.» en vez de ir al login, y el interceptor necesita una excepción especial para esa URL.

Cambio: la contraseña actual incorrecta es un **error de validación del formulario** → **400** con el mensaje
«La contraseña actual no es correcta.». El 401 queda solo para autenticación (token). El login no cambia
(sigue 401 «Credenciales inválidas»).

## Archivos a tocar
```
src/main/java/com/bellidental/identity/application/AuthService.java   (modif.) changePassword
src/test/java/com/bellidental/identity/UserManagementIT.java          (modif.) esperar 400 y el mensaje
docs/IMPLEMENTATION-PLAN.md                                           §4.13: PUT /auth/password, 400 si la actual no coincide
```

## Criterios de aceptación
1. En `AuthService.changePassword`, si `!passwordEncoder.matches(currentPassword, hash)` → lanzar
   `InvalidInputException("La contraseña actual no es correcta.")` (la misma clase que ya usa para «La nueva
   contraseña debe ser distinta de la actual.»; el handler global responde 400 con ese `message`).
   Quitar el import de `BadCredentialsException` si queda sin uso.
2. Nada más cambia: `isAuthenticated()` sigue en el controlador, así que un token inválido o vencido sigue dando
   401 antes de llegar al servicio.

## Tests requeridos
**Unit:** ninguno.

**Integration** (`UserManagementIT.createUser_asAdmin_canLoginAndChangeOwnPassword`, línea ~61): la llamada con la
contraseña actual incorrecta pasa a esperar `HttpStatus.BAD_REQUEST` y que el cuerpo contenga
`"La contraseña actual no es correcta."`. Sin casos nuevos.

## Resultado
- **Qué se hizo**:
  - Modificado `AuthService.changePassword` para lanzar `InvalidInputException("La contraseña actual no es correcta.")` cuando `!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())`, resultando en respuesta HTTP 400. Se mantuvo el import de `BadCredentialsException` porque se sigue utilizando en el método `login`.
  - Actualizado `UserManagementIT.createUser_asAdmin_canLoginAndChangeOwnPassword` para verificar `HttpStatus.BAD_REQUEST` y comprobar que el mensaje sea `"La contraseña actual no es correcta."`.
  - Actualizado `docs/IMPLEMENTATION-PLAN.md` en §4.13 para reflejar que `PUT /auth/password` responde 400 si la contraseña actual no coincide.
- **Resultado de los tests**:
  - Surefire: Tests run: 103, Failures: 0, Errors: 0, Skipped: 0.
  - Failsafe: Tests run: 76, Failures: 0, Errors: 0, Skipped: 0 (incluyendo `UserManagementIT`: 3 tests).
- **Problemas encontrados y supuestos tomados**:
  - Ninguno. El manejador global ya mapea `InvalidInputException` a HTTP 400 Bad Request con el mensaje correspondiente.

## Revisión

Estado: APROBADO

Cumple los criterios 1–2: `InvalidInputException` → 400 con el mensaje; `BadCredentialsException` se conserva porque
la usa `login`. El IT espera 400 y el mensaje. `UserManagementIT` 3/3 (0 fallos).
