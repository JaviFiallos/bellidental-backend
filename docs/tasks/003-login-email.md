# 003 — Autenticación por email con JWT

## Objetivo
Permitir que los usuarios semilla (tarea 002) inicien sesión por email y obtengan un JWT, cerrar las rutas
que hoy quedan públicas y dejar `CurrentUser` listo para los módulos siguientes (RNF01; plan §2).

## Archivos a tocar
- `config/SecurityConfig.java`: `@Configuration`, `@EnableWebSecurity`, `@EnableMethodSecurity`, constructor;
  rutas públicas solo las del criterio 7; `AuthenticationEntryPoint` y `AccessDeniedHandler` en JSON.
- `security/CustomUserDetailsService.java`, `security/JwtService.java`, `security/JwtAuthenticationFilter.java`
- `security/CurrentUser.java` (nuevo)
- `persistence/repository/UserRepository.java`: búsqueda por email y por id (no borrados).
- `identity/api/AuthController.java`, `identity/application/AuthService.java`,
  `identity/api/dto/LoginRequest.java`, `identity/api/dto/AuthResponse.java`, DTO nuevo para `/me`.
- Tests: `src/test/java/com/bellidental/identity/` y `src/test/java/com/bellidental/security/`.

## Criterios de aceptación
1. `POST /api/v1/auth/login` con `{email, password}` (`LoginRequest` cambia `username` por `email`, `@Email @NotBlank`)
   → `200` con `ApiResponse<AuthResponse>` y `AuthResponse = {accessToken, expiresIn, roles}`
   (`expiresIn` en segundos; `roles` = códigos de rol). Se deja de devolver `username`.
2. Credenciales incorrectas, email inexistente, usuario inactivo, borrado (`deleted_at`) o `is_system=true` → `401` con
   el mismo mensaje genérico ("Credenciales inválidas"); la respuesta no revela cuál fue la causa.
3. `CustomUserDetailsService` carga por **email**. Authorities: `ROLE_<code>` de cada rol **más** el código de cada
   permiso de esos roles (p. ej. `ROLE_SECRETARIA`, `BILLING_CREATE`, `SCHEDULE_MANAGE`).
4. Login exitoso actualiza `last_login_at` (`OffsetDateTime`).
5. Secreto y expiración desde `application.security.jwt.*`; no se registran contraseñas ni tokens en logs.
6. `GET /api/v1/auth/me` (autenticado) devuelve `{userId, email, roles, permissions, professionalId}`
   (`professionalId` null si el usuario no es profesional).
7. Rutas públicas: solo `POST /api/v1/auth/login` y Swagger/OpenAPI (`/swagger-ui/**`, `/swagger-ui.html`,
   `/api-docs/**`, `/v3/api-docs/**`). Hoy `/api/v1/auth/**` es público y deja abiertos `/register` y `/me`.
8. Quitar `POST /api/v1/auth/register` del controlador y `AuthService.register` si queda sin uso. `UserService`,
   `UserController` (solo ADMIN, borrado lógico) y sus DTOs se conservan sin cambios funcionales (MVP-SCOPE).
9. JWT: `sub` = `user_id`; claims `email`, `roles` y `professionalId` (si existe). El usuario sin profesional no lleva claim.
10. `JwtAuthenticationFilter`: valida el token y recarga el usuario por `user_id` en cada petición; si está inactivo,
    borrado o es `is_system`, la petición queda sin autenticar (→ 401) aunque el token no haya expirado.
11. `CurrentUser` (bean en `security`): `userId()` → `UUID` y `professionalId()` → `Optional<UUID>`, leídos del
    usuario autenticado. Sin usuario autenticado lanza una excepción clara (el bot, sin HTTP, se resolverá en la tarea 015).
12. 401 y 403 responden JSON con el formato de `ApiResponse` / `GlobalExceptionHandler` (nunca HTML ni cuerpo vacío),
    también cuando el error ocurre en el filtro, antes de llegar al controlador.
13. Sin el WARN de arranque "Global AuthenticationManager configured with an AuthenticationProvider bean":
    un único mecanismo (`DaoAuthenticationProvider` con `CustomUserDetailsService` y el `PasswordEncoder` existente).
14. No se crean permisos, roles ni migraciones nuevas.

## Tests requeridos
- Unit `AuthServiceTest` (`identity`): rechaza inactivo, borrado e `is_system` con el mismo mensaje; login correcto
  actualiza `last_login_at` y devuelve roles.
- Unit `JwtServiceTest` (`security`): genera y lee `sub`, `roles` y `professionalId`; token expirado → inválido;
  firma alterada → inválido.
- Integration `AuthControllerIT` (`identity`, con `TestcontainersConfiguration`):
  1. login de secretaria → 200 con token y rol `SECRETARIA`;
  2. contraseña errónea → 401 JSON;
  3. bot_whatsapp → 401;
  4. `/me` sin token → 401 JSON; con token de odontologa → 200 con `professionalId` no nulo;
  5. `POST /api/v1/auth/register` → no 200/201 (endpoint eliminado).

## Resultado

**Archivos modificados/creados:**
- `User.java`, `UserResponse.java`: Actualizado de `LocalDateTime` a `OffsetDateTime`.
- `LoginRequest.java`: Usar `email`.
- `AuthResponse.java`: Renombrado a `accessToken`, `expiresIn`, y `roles`.
- `MeResponse.java` (Nuevo): DTO para `/api/v1/auth/me`.
- `RegisterUserRequest.java`: Eliminado (ya no hay register).
- `UserAuthorities.java` (Nuevo): Componente unificado para extraer roles y permisos de `User`.
- `AuthService.java`: Implementado `login` y `me` haciendo uso de `UserAuthorities`.
- `AuthController.java`: Limpiado register, añadido `GET /me`.
- `CustomUserDetailsService.java`: Lanza `UsernameNotFoundException` y construye el `UserDetails` usando `UserAuthorities`.
- `JwtService.java`: `sub=userId`, nuevos claims `email, roles, professionalId`. Captura `JwtException`.
- `JwtAuthenticationFilter.java`: Extrae el UUID y usa `UserAuthorities` para poblar el Principal. Importa `JwtException` correctamente.
- `UserRepository.java`: Agregado `findWithRolesByIdAndDeletedAtIsNull` con `@EntityGraph` correctamente importado.
- `SecurityConfig.java`: Configurado JSON exception handlers. Eliminada variable `PUBLIC_ENDPOINTS` sin uso.
- `GlobalExceptionHandler.java`: Maneja `BadCredentialsException` regresando 401. (Se removió `InternalAuthenticationServiceException`).
- `CurrentUser.java` (Nuevo): Expone `userId` y `professionalId` al resto de la aplicación.
- Tests (nuevos): `AuthServiceTest`, `JwtServiceTest`, `AuthControllerIT`.

**Resultado de los tests:**
- Surefire: `[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`
- Failsafe: `[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0`

**Problemas encontrados y supuestos:**
- **LazyInitializationException en JwtAuthenticationFilter:** Resuelto con `@EntityGraph` en `UserRepository`.
- **Excepciones subyacentes:** Las excepciones internas quedaban enmascaradas con 401. Ahora se confía en que `DaoAuthenticationProvider` convierta explícitamente los `UsernameNotFoundException` en `BadCredentialsException` y que fallos de infraestructura sigan fluyendo a 500. Se usa `UserAuthorities` para evitar código duplicado en la lógica de claims.

## Revisión
**Estado: CORRECCIONES** (revisión 1, Claude)

Verificado por Claude con `./mvnw clean verify`: surefire `AuthServiceTest` 4/4, `JwtServiceTest` 3/3; failsafe
`AuthControllerIT` 6/6, `BaseSeedIT` 4/4, `SchemaMigrationIT` 1/1. El WARN del criterio 13 ya no aparece; el log no
contiene tokens ni contraseñas. Criterios 1–12 y 14 cumplidos en lo funcional. `User` a `OffsetDateTime` y borrar
`RegisterUserRequest` (sin uso): aceptados.

Corregir:
1. `GlobalExceptionHandler`: quitar `InternalAuthenticationServiceException` del 401. Spring la lanza también ante
   fallos internos (p. ej. base de datos caída) y así quedarían ocultos como "Credenciales inválidas".
   En su lugar, `CustomUserDetailsService` lanza `UsernameNotFoundException` cuando no existe el email, el usuario
   está inactivo o es `is_system`. `DaoAuthenticationProvider` ya la convierte en `BadCredentialsException`.
2. Duplicación: la construcción de authorities (`ROLE_<code>` + permisos) está copiada en `CustomUserDetailsService` y
   `JwtAuthenticationFilter`, y la de códigos de rol/permiso en `AuthService.login` y `me`. Extraer un único
   componente en `security` (p. ej. `UserAuthorities`) y usarlo en los tres sitios.
3. `SecurityConfig`: `PUBLIC_ENDPOINTS` quedó sin uso; usarlo en `requestMatchers` (con el POST de login aparte)
   o eliminarlo.
4. Imports en lugar de nombres totalmente calificados: `io.jsonwebtoken.JwtException` (filtro) y
   `EntityGraph` (`UserRepository`).
5. Correr `./mvnw clean verify` de nuevo y pegar en Resultado las líneas reales `Tests run:` de surefire y failsafe.

**Estado: APROBADO** (revisión 2, Claude)

Correcciones 1–5 aplicadas: `UsernameNotFoundException` en lugar de mapear `InternalAuthenticationServiceException`;
`UserAuthorities` único usado por `CustomUserDetailsService`, `JwtAuthenticationFilter` y `AuthService`;
`PUBLIC_ENDPOINTS` eliminado; imports corregidos. Claude cambió el último nombre calificado
(`io.jsonwebtoken.JwtException` en `JwtService`) por import.
`./mvnw clean verify` (Claude): surefire 7/7 (`AuthServiceTest` 4, `JwtServiceTest` 3); failsafe 11/11
(`AuthControllerIT` 6, `BaseSeedIT` 4, `SchemaMigrationIT` 1).

Commit: código y tests de la tarea + este archivo. No incluir `proy-infor/`.
