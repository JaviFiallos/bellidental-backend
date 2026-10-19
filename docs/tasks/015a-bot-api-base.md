# 015a — Bot (Java): API interna base

## Objetivo
Primera pieza del bot híbrido (plan §4.11, D-19). Crea el módulo `com.bellidental.bot` con una API interna en
`/api/v1/bot/*`, que solo usa el agente Python con la clave de servicio `X-Bot-Key`. Esta tarea cubre:
- la migración V13;
- la autenticación por clave;
- el consentimiento del aviso de privacidad (RF10);
- la información pública de la clínica (horarios, formas de pago, precios);
- la identificación del paciente por teléfono.

El agente nunca accede a la BD: todo pasa por aquí. El bot no ve datos clínicos ni cobros (RNF03).

**Estructura del módulo**
- `bot` no expone `Directory`: nadie lo consume dentro del backend.
- Depende de `PatientDirectory` y de `TreatmentDirectory`, que es nueva y es la interfaz pública mínima de
  `treatments`.

## Archivos a tocar
```
pom.xml                                     failsafe: <environmentVariables> BOT_API_KEY=it-bot-key
src/main/resources/db/migration/V13__bot.sql   (nuevo) appointment_reminders y privacy_consents tal como
                                            schema.dbml §12 (columnas, defaults, índices y FKs de RELACIONES)
src/main/resources/application.yml          + application.bot.* y nuevos application.clinic.* (ver criterio 6)
config/
  ClinicProperties.java                     + hours (List<String>), paymentMethods (List<String>),
                                              receptionWhatsapp, afterHoursPhone
  BotProperties.java                        (nuevo) @ConfigurationProperties("application.bot"):
                                              apiKey, privacyNoticeVersion, publicTreatments (List<String>)
  SecurityConfig.java                       /api/v1/bot/** → hasAuthority("BOT"); registra BotApiKeyFilter
security/
  BotApiKeyFilter.java                      (nuevo) OncePerRequestFilter, solo para /api/v1/bot/**
persistence/
  entity/PrivacyConsent.java                (nuevo)
  repository/PrivacyConsentRepository.java  (nuevo)
treatments/
  TreatmentDirectory.java                   (nuevo) List<TreatmentPriceSummary> findActiveByCodes(Collection<String>)
  TreatmentPriceSummary.java                (nuevo) record(code, name, BigDecimal price)
  application/TreatmentDirectoryImpl.java   (nuevo)
bot/
  api/BotController.java                    (nuevo) consents, info, patients
  api/dto/                                  (nuevo) ConsentRequest, ConsentResponse, ClinicInfoResponse,
                                              PublicPriceResponse, BotPatientResponse, VerifyPatientRequest
  application/BotConsentService.java        (nuevo)
  application/BotIdentityService.java       (nuevo) pacientes del teléfono, verificación por cédula,
                                              requirePatientOfPhone(phone, patientId) (lo usan 016a/016b)
  application/BotInfoService.java           (nuevo)
src/test/java/com/bellidental/bot/BotApiIT.java   (nuevo)
```
La entidad de `appointment_reminders` no se crea aquí: llega en la 017a. La tabla queda creada por V13.

## Criterios de aceptación
1. **Clave de servicio**:
   - `BotApiKeyFilter` actúa solo en `/api/v1/bot/**`.
   - Si `X-Bot-Key` coincide con `application.bot.api-key` (comparación en tiempo constante, `MessageDigest.isEqual`)
     y la clave configurada no está vacía, autentica con:
     - principal `CurrentUser.CurrentUserPrincipal(<id del usuario bot_whatsapp de V4: 44444444-…>, Optional.empty())`;
     - autoridad única `BOT`.
   - Sin clave o con clave errónea → `401`.
   - Un JWT de personal en `/api/v1/bot/**` → `403`, porque no tiene `BOT`.
   - Fuera de `/api/v1/bot/**` el header se ignora: `X-Bot-Key` no da acceso a nada más.
   - La autoridad `BOT` existe solo en código; la semilla no cambia.
2. **Teléfono**: todo `phone` se normaliza con `PhoneNumberNormalizer`. WhatsApp lo envía como `593987654321`,
   igual que lo guarda `patients.phone_e164`.
3. **Consentimiento (RF10)**:
   - `GET /api/v1/bot/consents?phone=` → `{accepted, noticeVersion}`. `accepted` = existe una fila con ese teléfono
     y `notice_version` = `application.bot.privacy-notice-version`.
   - `POST /api/v1/bot/consents` `{phone, patientId?}` → `201` con la misma respuesta.
     - Es idempotente: si ya hay una aceptación de esa versión, no crea otra.
     - Si viene `patientId`, debe ser un paciente activo con ese teléfono; si no → `404`.
4. **Información**: `GET /api/v1/bot/info` →
   `{clinicName, address, hours[], paymentMethods[], receptionWhatsapp, afterHoursPhone, prices[{code, name, price}]}`.
   - `prices` = tratamientos activos cuyos códigos están en `application.bot.public-treatments`, vía
     `TreatmentDirectory`, en el orden de la configuración.
5. **Identificación**:
   - `GET /api/v1/bot/patients?phone=` → pacientes activos del teléfono, cada uno como `{patientId, displayName}`.
     `displayName` = primer nombre + inicial del primer apellido (`"Juan P."`). Nunca se devuelven la cédula ni el
     apellido completo.
   - `POST /api/v1/bot/patients/verify` `{phone, idNumber}` → `{patientId, displayName}` si un paciente activo de
     ese teléfono tiene ese `document_number`.
   - Si no → `404` con el mensaje `"Paciente no encontrado."`. Es el mismo mensaje para cualquier caso, para que no
     sirva para enumerar cédulas.
   - `requirePatientOfPhone(phone, patientId)` lanza `ResourceNotFoundException` si el paciente no está activo o no
     tiene ese teléfono.
6. **Configuración** (`application.yml`; los valores de la clínica son provisionales):
   ```yaml
   application:
     bot:
       api-key: ${BOT_API_KEY:}            # vacía = API del bot deshabilitada (todo 401)
       privacy-notice-version: "v1"
       public-treatments: [D0150, D1110, D2330]
     clinic:
       hours: ["Lunes a viernes 09:00–13:00 y 15:00–19:00", "Sábados 09:00–13:00"]
       payment-methods: ["Efectivo", "Transferencia"]
       reception-whatsapp: "593000000000"
       after-hours-phone: "593000000000"
   ```
7. Todas las respuestas usan `ApiResponse`. `bot` usa solo `PatientDirectory` y `TreatmentDirectory`: nunca
   repositorios de otros módulos ni nada de `clinical`, `clinicalrecords`, `odontogram`, `prescriptions` o `billing`.
8. **Orden de migraciones**: V13 puede aplicarse antes de que existan V11 (012) y V12 (013).
   - No se renumera.
   - Si la BD local de desarrollo ya tiene V13 cuando lleguen V11/V12, se recrea la BD local.
   - Los tests y el VPS parten de una BD vacía.

## Tests requeridos
**Unit**: ninguno.

**Integration** (`BotApiIT`, 3 casos; clave `it-bot-key` por la variable de failsafe):
1. `botApi_withoutOrWrongKeyOrStaffJwt_isRejected`:
   - sin header → 401;
   - clave errónea → 401;
   - JWT de `secretaria` en `/api/v1/bot/info` → 403;
   - `X-Bot-Key` válida en `GET /api/v1/patients` → 401.
2. `consent_acceptThenQuery_isAcceptedAndIdempotent`:
   - `GET /consents` → `accepted=false`;
   - `POST` dos veces → 201;
   - `GET` → `accepted=true`;
   - en la BD hay una sola fila para ese teléfono.
3. `identify_byPhoneAndVerify_returnsDisplayNameOnly`:
   - con dos pacientes creados por la API de personal con el mismo teléfono, `GET /patients` devuelve 2 con
     `displayName` y sin cédula;
   - `verify` con la cédula de uno → su id;
   - con una cédula ajena → 404.

## Resultado
- **Archivos creados o modificados:**
  - `pom.xml`: se agregó la variable de entorno `BOT_API_KEY=it-bot-key` a `maven-failsafe-plugin`.
  - `src/main/resources/db/migration/V13__bot.sql` (nuevo): migración con tablas `appointment_reminders` y `privacy_consents`, con sus restricciones `CHECK`, índices y claves foráneas según `schema.dbml §12`.
  - `src/main/resources/application.yml`: configuración de `application.bot` (`api-key`, `privacy-notice-version`, `public-treatments`) y datos adicionales de `application.clinic` (`hours`, `payment-methods`, `reception-whatsapp`, `after-hours-phone`).
  - `config/ClinicProperties.java`: se añadieron los campos `zone`, `hours`, `paymentMethods`, `receptionWhatsapp` y `afterHoursPhone`.
  - `config/BotProperties.java` (nuevo): record `@ConfigurationProperties("application.bot")` con `apiKey`, `privacyNoticeVersion` y `publicTreatments`.
  - `security/BotApiKeyFilter.java` (nuevo): filtro `OncePerRequestFilter` exclusivo para `/api/v1/bot/**` con validación de cabecera `X-Bot-Key` en tiempo constante (`MessageDigest.isEqual`), asignando la autoridad `BOT` y el principal `CurrentUserPrincipal` con el UUID de `bot_whatsapp`.
  - `config/SecurityConfig.java`: autorización `.requestMatchers("/api/v1/bot/**").hasAuthority("BOT")` y registro de `BotApiKeyFilter` antes de `LogoutFilter.class`.
  - `persistence/entity/PrivacyConsent.java` (nuevo): entidad JPA para la tabla `privacy_consents`.
  - `persistence/repository/PrivacyConsentRepository.java` (nuevo): repositorio con búsquedas por teléfono y versión de aviso.
  - `persistence/repository/TreatmentRepository.java`: se agregó `findByCodeInAndActiveTrue(Collection<String>)`.
  - `treatments/TreatmentPriceSummary.java` (nuevo): record público con `code`, `name` y `price`.
  - `treatments/TreatmentDirectory.java` (nuevo): interfaz pública con `findActiveByCodes(Collection<String>)`.
  - `treatments/application/TreatmentDirectoryImpl.java` (nuevo): implementación que consulta tratamientos activos preservando el orden configurado.
  - `bot/api/dto/` (nuevos): `ConsentRequest`, `ConsentResponse`, `ClinicInfoResponse`, `PublicPriceResponse`, `BotPatientResponse`, `VerifyPatientRequest`.
  - `bot/application/BotIdentityService.java` (nuevo): consulta de pacientes activos por teléfono, formateo de `displayName` (`"Nombre I."`), verificación por documento e invariante `requirePatientOfPhone`.
  - `bot/application/BotConsentService.java` (nuevo): consulta y registro idempotente de consentimiento de privacidad.
  - `bot/application/BotInfoService.java` (nuevo): entrega de datos de la clínica y precios públicos vía `TreatmentDirectory`.
  - `bot/api/BotController.java` (nuevo): endpoints bajo `/api/v1/bot/` (`/consents`, `/info`, `/patients`, `/patients/verify`).
  - `src/test/java/com/bellidental/bot/BotApiIT.java` (nuevo): 3 tests de integración requeridos (autenticación y rechazo 401/403, consentimiento idempotente, identificación con displayName y verificación).

- **Resultado de los tests:**
  - Surefire: 89 tests ejecutados, 0 fallos, 0 errores, 0 omitidos.
  - Failsafe: 49 tests ejecutados, 0 fallos, 0 errores, 0 omitidos (`BotApiIT`: 3 tests OK).
  - Total: 138 tests pasando exitosamente.

- **Problemas encontrados y supuestos tomados:**
  - En Spring Security, se registró `BotApiKeyFilter` antes de `LogoutFilter.class` (que a su vez precede a `UsernamePasswordAuthenticationFilter`) para respetar las clases con orden registrado en Spring Security y garantizar que `BotApiKeyFilter` procese la cabecera antes de la evaluación de autorización.
  - Las respuestas se envolvieron en `ApiResponse.ok(...)` de acuerdo al estándar del backend.
  - Para la creación de pacientes en `BotApiIT`, se utilizó `DocumentType.CI` con generación dinámica de cédulas ecuatorianas válidas que satisfacen el algoritmo módulo 10 de `EcuadorianIdValidator`.

## Revisión
Estado: APROBADO

1. Clave de servicio: el filtro actúa solo en `/api/v1/bot`, compara en tiempo constante y autentica como
   `bot_whatsapp` con la autoridad única `BOT`. `SecurityConfig` exige `BOT` en `/api/v1/bot/**`. `BotApiIT` cubre
   los cuatro rechazos: sin clave, clave errónea, JWT de personal y la clave usada en `/patients`.
2. Consentimiento idempotente, `displayName` sin cédula, `verify` con el mismo 404 en cualquier caso y precios en el
   orden de la configuración: cumplen.
3. La configuración de la clínica está bajo `application.clinic` y la del bot bajo `application.bot`. `bot` solo usa
   `PatientDirectory` y `TreatmentDirectory`.
4. `./mvnw -q clean verify` corrido por Claude: surefire 89/0/0/0, failsafe 52/0/0/0. El total incluye los 3 casos de
   `BotAvailabilityIT` de la 016a, que ya está en el mismo árbol de trabajo.
5. V13 incluye los `CHECK` de `kind`, `status` y `response` y el índice único parcial `(appointment_id, kind) WHERE
   status='PENDING'`. Ambos están en las notas SQL de `schema.dbml`: la migración sigue el esquema.
   (Corrige una observación anterior que los daba por extras.)
