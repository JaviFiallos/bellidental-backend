# 026 — Enlace temporal firmado para los PDF (receta y comprobante) (G-14 del frontend)

## Objetivo
Hoy el frontend descarga el PDF con `fetch` + Bearer y lo abre como `blob:` en una pestaña nueva. En móviles y
tablets (Safari iOS, Chrome Android) el `blob:` abierto en otra pestaña falla a menudo: pestaña en blanco, descarga que
no arranca, o el visor del sistema no puede leerlo porque el blob vive en la página que lo creó (y se revoca al minuto).

Cambio: el frontend pide un **enlace temporal firmado** (5 min) y abre una URL https normal del mismo origen, sin
Bearer. Así el visor de PDF del dispositivo, "Descargar" y "Compartir" funcionan como con cualquier archivo.
Un enlace "limpio" **sin token** no se hace: dejaría la receta (dato de salud, LOPDP) pública para cualquiera con la URL.

## Archivos a tocar
```
src/main/java/com/bellidental/security/
  DocumentLinkService.java            (nuevo) firmar y verificar el token del documento
src/main/java/com/bellidental/shared/api/
  DocumentLinkResponse.java           (nuevo) record { url, expiresAt }
src/main/java/com/bellidental/security/JwtAuthenticationFilter.java   (modif.) rechazar tokens de documento como Bearer
src/main/java/com/bellidental/config/SecurityConfig.java              (modif.) permitAll de los 2 GET públicos
src/main/java/com/bellidental/prescriptions/api/PrescriptionController.java  (modif.) POST pdf-link + GET receta.pdf
src/main/java/com/bellidental/billing/api/PaymentController.java             (modif.) POST receipt-link + GET comprobante.pdf
src/main/resources/application.yml                                   (modif.) application.security.document-link.ttl-seconds: 300
src/test/java/com/bellidental/prescriptions/PrescriptionPdfIT.java   (modif.) casos nuevos
src/test/java/com/bellidental/billing/PaymentFlowIT.java             (modif.) 1 caso nuevo
docs/IMPLEMENTATION-PLAN.md                                          §4.7, §4.8, tabla §3 y D-28
```
(Si `shared/api` no existe, el record va junto a `DocumentLinkService` en `security/`.)

## Criterios de aceptación
1. `DocumentLinkService` usa la misma clave que `JwtService` (`application.security.jwt.secret`) y genera un JWT con
   `subject` = id del documento, claims `purpose: "document"` y `doc: "prescription" | "receipt"`, y `exp` = ahora +
   `ttl-seconds` (300 por defecto). `verify(token, doc, id)` → `true` solo si la firma es válida, no venció,
   `purpose` y `doc` coinciden y `subject` = `id`.
2. `POST /api/v1/prescriptions/{id}/pdf-link` (`RECORD_READ`) → 200 `ApiResponse<DocumentLinkResponse>` con
   `url = "/api/v1/prescriptions/{id}/receta.pdf?t=<token>"` (relativa) y `expiresAt` (`OffsetDateTime`).
   Receta no emitida → 409 con el mismo mensaje que hoy da `GET …/pdf`; no existe → 404.
3. `GET /api/v1/prescriptions/{id}/receta.pdf?t=` es **público** en `SecurityConfig` (solo ese patrón y método GET).
   Con token válido → 200 `application/pdf`, el mismo cuerpo y `Content-Disposition: inline` que `GET …/pdf`.
   Token ausente, inválido, vencido o de otro documento → 403 `ApiResponse` con `message`
   "El enlace del documento venció o no es válido.".
4. Igual para cobros: `POST /api/v1/payments/{id}/receipt-link` (`BILLING_READ`) →
   `url = "/api/v1/payments/{id}/comprobante.pdf?t=<token>"`; `GET …/comprobante.pdf?t=` público con las mismas reglas.
5. Ambas respuestas PDF públicas llevan `Cache-Control: no-store`, `Referrer-Policy: no-referrer` y
   `X-Content-Type-Options: nosniff`.
6. `JwtAuthenticationFilter`: un token con claim `purpose` **no** autentica como Bearer (se ignora y la petición
   sigue sin autenticación → 401 en endpoints protegidos). El token de sesión no sirve como `t`.
7. Los `GET …/pdf` y `GET …/receipt` con Bearer **no cambian** (compatibilidad; se quitan cuando el frontend 013c
   esté aprobado, en otra tarea si se decide).

## Tests requeridos
**Unit:** ninguno.

**Integration:**
- `PrescriptionPdfIT` (+3): receta emitida → `pdf-link` con Bearer de odontólogo → `GET url` **sin** Bearer → 200
  `application/pdf`; token de otra receta → 403 con el mensaje; usar el token del enlace como Bearer en
  `GET /auth/me` → 401.
- `PaymentFlowIT` (+1): `receipt-link` → `GET url` sin Bearer → 200 `application/pdf`.

## Resultado
- **Qué se hizo:**
  - Creado `DocumentLinkResponse.java` en `com.bellidental.security` (`url`, `expiresAt`) como indicaba la tarea al no existir `shared/api`.
  - Creado `DocumentLinkService.java` en `com.bellidental.security` para firmar y validar tokens JWT con `subject = id`, `purpose: "document"` y `doc: "prescription" | "receipt"`, con TTL configurable de 300 s.
  - Añadido `application.security.document-link.ttl-seconds: 300` en `application.yml`.
  - Modificado `JwtAuthenticationFilter.java` para ignorar tokens que contengan claim `purpose`, impidiendo su uso como Bearer de autenticación.
  - Modificado `SecurityConfig.java` para habilitar `permitAll` en `GET /api/v1/prescriptions/*/receta.pdf` y `GET /api/v1/payments/*/comprobante.pdf`.
  - Modificado `PrescriptionController.java`: `POST /prescriptions/{id}/pdf-link` (`RECORD_READ`) y `GET /prescriptions/{id}/receta.pdf?t=` público con cabeceras `Cache-Control: no-store`, `Referrer-Policy: no-referrer`, `X-Content-Type-Options: nosniff`.
  - Modificado `PaymentController.java`: `POST /payments/{id}/receipt-link` (`BILLING_READ`) y `GET /payments/{id}/comprobante.pdf?t=` público con idénticas cabeceras.
  - Modificado `PrescriptionPdfIT.java` (+3 casos) y `PaymentFlowIT.java` (+1 caso).
  - Actualizado `docs/IMPLEMENTATION-PLAN.md` en §4.7 y §4.8.
- **Resultado de los tests:**
  - Surefire: `Tests run: 103, Failures: 0, Errors: 0, Skipped: 0`
  - Failsafe: `Tests run: 86, Failures: 0, Errors: 0, Skipped: 0`
- **Problemas y supuestos:**
  - Al no existir `shared/api`, `DocumentLinkResponse` se ubicó en `com.bellidental.security` junto a `DocumentLinkService`, siguiendo la indicación de la tarea.


## Revisión
Estado: APROBADO

Cumple los 7 criterios; IMPLEMENTATION-PLAN actualizado (§3, §4.7, §4.8, D-28). Correcciones menores hechas por Claude:
1. `PrescriptionController.pdf-link` duplicaba la regla "solo receta emitida": ahora llama a
   `PrescriptionPdfService.requirePrintable(id)` (nuevo, reutiliza `requireIssued`).
2. Los controladores usan `DocumentLinkService.DOC_PRESCRIPTION` / `DOC_RECEIPT` en lugar de literales.
3. `DocumentLinkService.verify`: se quitó la sobrecarga con `String id` sin uso.

`./mvnw -q clean verify` (Claude): Surefire 103/0/0/0 · Failsafe 86/0/0/0.
