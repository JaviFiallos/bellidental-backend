# 003 — Autenticación por email con JWT

## Objetivo
Permitir que los usuarios semilla inicien sesión por email y obtengan un JWT.

## Archivos a tocar
- `config/SecurityConfig.java`: añadir `@Configuration`, `@EnableWebSecurity`,
  `@EnableMethodSecurity` y constructor; rutas públicas solo las del criterio 7.
- `security/CurrentUser.java` (nuevo): acceso al usuario autenticado.
- `security/CustomUserDetailsService.java`, `security/JwtService.java`, `security/JwtAuthenticationFilter.java`
- `identity/api/AuthController.java`, `identity/application/AuthService.java` (+ DTOs)
- Tests en `src/test/java/com/bellidental/identity/`

## Criterios de aceptación
1. `POST /api/v1/auth/login` con `{email, password}` → `200 {accessToken, expiresIn, roles}`.
2. Credenciales incorrectas, usuario inactivo, borrado (`deleted_at`) o `is_system=true` → `401` con mensaje genérico.
3. Authorities: `ROLE_<code>` de cada rol + códigos de permiso.
4. Login exitoso actualiza `last_login_at`.
5. Secreto y expiración desde `application.security.jwt.*`; no se registran contraseñas ni tokens en logs.
6. Endpoint protegido de prueba `GET /api/v1/auth/me` devuelve email, roles y `professionalId` (o null).
7. Rutas públicas: solo `POST /api/v1/auth/login` y Swagger/OpenAPI. Hoy `/api/v1/auth/**` es público:
   eso deja abiertos `/register` y `/me`.
8. Quitar el endpoint `POST /api/v1/auth/register` del controlador (el servicio de usuarios se conserva,
   pero no se expone; RNF01). `UserController` sigue solo para ADMIN, sin cambios funcionales.
9. JWT: `sub` = `user_id`, más `email`, `roles` y `professionalId` (si el usuario tiene profesional).
   `CurrentUser` expone `userId()` y `professionalId()` (Optional) leyendo el token; lo usarán los módulos siguientes.
10. 401 y 403 responden JSON con el mismo formato que `GlobalExceptionHandler` (sin cuerpo HTML ni vacío).
11. Sin el WARN de arranque "Global AuthenticationManager configured with an AuthenticationProvider bean":
    usar un único mecanismo (el `DaoAuthenticationProvider` con `CustomUserDetailsService`).

## Tests requeridos
- Unit `AuthServiceTest`: rechaza inactivo, borrado y `is_system`; actualiza `last_login_at`.
- Unit `JwtServiceTest`: genera y lee `sub`, roles y `professionalId`; token expirado o con firma alterada → inválido.
- Integration `AuthControllerIT`: login correcto de secretaria devuelve token; contraseña errónea → 401 JSON;
  bot_whatsapp → 401; `/me` sin token → 401 y con token de odontologa → 200 con `professionalId`;
  `POST /api/v1/auth/register` ya no existe (no 200/201).

## Resultado
_(Lo llena Gemini)_
