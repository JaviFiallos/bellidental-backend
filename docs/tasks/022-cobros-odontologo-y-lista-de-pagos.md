# 022 — Cobros: el odontólogo cobra y lista de pagos del paciente (V18)

## Objetivo
Dos cambios en `com.bellidental.billing` pedidos por el usuario (2026-10-22):
1. **El odontólogo también cobra** (decisión **D-8d**, reemplaza la parte de D-8b «cobran SECRETARIA y ADMIN»):
   el rol ODONTOLOGO recibe `BILLING_CREATE` y `BILLING_READ`. Es una migración **solo de datos** (V18), sin
   cambios de esquema. Con `BILLING_CREATE` también puede anular pagos con motivo (queda en Envers).
2. **Lista de pagos del paciente** (G-11 del frontend): hoy `GET /patients/{id}/account` solo trae sumas por sesión
   y no hay forma de reimprimir o anular un abono de otro día. Se añade
   `GET /api/v1/patients/{patientId}/payments` con los pagos (vigentes y anulados) y quién los recibió.

## Archivos a tocar
```
src/main/resources/db/migration/V18__odontologo_billing.sql        (nuevo)
src/main/java/com/bellidental/
  persistence/repository/PaymentRepository.java                    (modif.) findByPatientIdOrderByPaidAtDesc
  billing/
    api/PaymentController.java                                     (modif.) GET /patients/{patientId}/payments
    api/dto/PaymentListItemResponse.java                           (nuevo)
    application/PaymentService.java                                (modif.) listByPatient
src/test/java/com/bellidental/
  billing/PaymentFlowIT.java                                       (modif.) reemplazar el caso del 403
  identity/BaseSeedIT.java                                         (modif.) matriz de permisos del odontólogo
docs/IMPLEMENTATION-PLAN.md                                        §4.8 reglas; §5 matriz; §6 D-8d
```
Sin dependencias nuevas entre módulos: el nombre de quien recibió el pago se resuelve con `UserRepository`
(`persistence`), igual que `AuditService`.

## Criterios de aceptación
1. **V18** exactamente (role_id 2 = ODONTOLOGO; permission_id 10 = BILLING_CREATE, 11 = BILLING_READ, ver V4):
   ```sql
   INSERT INTO role_permissions (role_id, permission_id) VALUES (2, 10), (2, 11)
   ON CONFLICT DO NOTHING;
   ```
   (Si `role_permissions` no tiene PK/UNIQUE compuesta y `ON CONFLICT` falla, usar
   `INSERT ... SELECT ... WHERE NOT EXISTS` y anotarlo en Resultado.)
2. `PaymentRepository`: `List<Payment> findByPatientIdOrderByPaidAtDesc(UUID patientId)`.
3. `GET /api/v1/patients/{patientId}/payments` (`@PreAuthorize("hasAuthority('BILLING_READ')")`) →
   `ApiResponse<List<PaymentListItemResponse>>`, más reciente primero, **incluye anulados**. 404 si el paciente no
   existe (`PatientDirectory.findById` vacío → `ResourceNotFoundException`, como en `register`).
   `PaymentListItemResponse(UUID id, UUID encounterId, BigDecimal amount, PaymentMethod method,
   String receiptNumber, OffsetDateTime paidAt, UUID receivedBy, String receivedByName, boolean voided,
   OffsetDateTime voidedAt, String voidReason)`.
   `receivedByName` = `username` del usuario (`UserRepository.findAllById` con los ids distintos, una sola consulta;
   `null` si no existe). Montos con escala 2.
4. El resto de endpoints de cobros no cambia. `JwtAuthenticationFilter` relee permisos en cada petición: no hay que
   tocar la seguridad.
5. Plan: §4.8 «SECRETARIA (BILLING_CREATE)…» pasa a «SECRETARIA y ODONTOLOGO (BILLING_CREATE)…» y añade el
   endpoint de lista; en §5 (matriz rol × módulo) el odontólogo pasa a tener cobros; en §6 añadir **D-8d**
   (confirmada por el usuario 2026-10-22) y en D-8b anotar «quién cobra: ver D-8d».

## Tests requeridos
**Unit:** ninguno.

**Integration:**
- `PaymentFlowIT`: **reemplazar** `createPayment_asOdontologo_returnsForbidden` por
  `createAndListPayments_asOdontologo_returnsBothWithVoided` (mismo setup de los otros casos, día propio 104):
  la odontóloga abona 10.00 → 201; la secretaria abona 5.00 y lo anula con motivo; `GET …/payments` como
  odontóloga → 200, 2 elementos, el primero el de 5.00 con `voided = true` y `voidReason` informado, y el segundo
  con `receivedByName = "odontologa"`.
- `PaymentFlowIT`: un caso nuevo `listPayments_unknownPatient_returnsNotFound` (UUID aleatorio → 404).
- `BaseSeedIT.rolePermissions_secretariaAndOdontologo_matchMatrix`: quitar `BILLING_CREATE` y `BILLING_READ` de la
  lista de permisos que el odontólogo **no** tiene y añadirlos a los que **sí** tiene.

## Resultado
- **Qué se hizo**:
  - Creada la migración `src/main/resources/db/migration/V18__odontologo_billing.sql` otorgando permisos `BILLING_CREATE` (10) y `BILLING_READ` (11) al rol `ODONTOLOGO` (2).
  - Añadido `findByPatientIdOrderByPaidAtDesc(UUID patientId)` en `PaymentRepository`.
  - Creado el record DTO `PaymentListItemResponse` en `com.bellidental.billing.api.dto`.
  - Añadido el método `listByPatient(UUID patientId)` en `PaymentService`, resolviendo nombres de usuario de los recibidores en una sola consulta mediante `UserRepository.findAllById`.
  - Expuesto el endpoint `GET /api/v1/patients/{patientId}/payments` en `PaymentController` con `@PreAuthorize("hasAuthority('BILLING_READ')")`.
  - Actualizados los tests: en `PaymentFlowIT`, se reemplazó el test de 403 por `createAndListPayments_asOdontologo_returnsBothWithVoided` y se añadió `listPayments_unknownPatient_returnsNotFound`; en `BaseSeedIT`, se actualizaron los permisos asignados al odontólogo.
  - Actualizado `docs/IMPLEMENTATION-PLAN.md` en §4.8, §5 y §6 (D-8d y nota en D-8b).
- **Resultado de los tests**:
  - Surefire: Tests run: 103, Failures: 0, Errors: 0, Skipped: 0.
  - Failsafe: Tests run: 76, Failures: 0, Errors: 0, Skipped: 0 (incluyendo `PaymentFlowIT`: 4 tests, `BaseSeedIT`: 4 tests).
- **Problemas encontrados y supuestos tomados**:
  - Ninguno. La migración V18 y el endpoint se ejecutaron sin conflictos de esquema ni de seguridad.

## Revisión

Estado: APROBADO

Cumple los criterios 1–5: V18 solo de datos (`role_permissions` tiene PK compuesta, `ON CONFLICT` válido), endpoint
con `BILLING_READ`, incluye anulados, nombres en una sola consulta, 404 para paciente inexistente.
Corrección mínima de Claude: `PaymentFlowIT.setUp` ahora llama a `clock.reset()` (como `DashboardSummaryIT`). Si un IT
del bot dejaba el `MutableClock` fijado, los dos abonos del caso nuevo tenían el mismo `paidAt` y el orden
`paidAt DESC` quedaba indefinido (test inestable). Verificado con `PaymentFlowIT` 4/4, `BaseSeedIT` 4/4 (0 fallos).
