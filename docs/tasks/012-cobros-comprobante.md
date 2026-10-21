# 012 — Cobros: abonos, estado de cuenta y comprobante PDF (V15)

## Objetivo
Módulo nuevo `com.bellidental.billing` (RF08, plan §4.8). La secretaria registra abonos del paciente (opcionalmente
ligados a la atención de la sesión), ve el estado de cuenta (cargos, abonos y saldo por sesión), descarga un
comprobante interno en PDF y puede anular un pago con motivo. El **saldo nunca se guarda**: se calcula.

Saldo = Σ(`price` × `quantity`) de tratamientos no anulados en atenciones no anuladas − Σ pagos no anulados.
Puede ser negativo (saldo a favor).

Decisiones (se anotan en el plan, §6; provisionales):
- **D-8b**: `BillingDirectory` **no se crea** (nadie la necesita). La 020 (estadísticas) lee agregados con consultas
  nativas (D-25).
- **D-8c**: el REVOKE DELETE de las notas SQL no se aplica por rol (la app y Flyway usan el mismo usuario). En su
  lugar, V15 crea un **trigger** `BEFORE DELETE` en `payments` que lanza excepción: así «sin DELETE sobre pagos» se
  cumple también en pruebas.

La carpeta `src/main/java/com/bellidental/billing/` ya existe vacía; se usa esa.

## Archivos a tocar
```
src/main/resources/db/migration/V15__payments.sql             (nuevo)
src/main/java/com/bellidental/
  shared/enums/PaymentMethod.java                             (nuevo) EFECTIVO, TRANSFERENCIA, TARJETA
  persistence/entity/Payment.java                             (nuevo)
  persistence/repository/PaymentRepository.java               (nuevo)
  treatments/PerformedTreatmentSummary.java                   (modif.) añadir `BigDecimal price` al final del record
  treatments/application/TreatmentDirectoryImpl.java          (modif.) pasar `pt.getPrice()` como último argumento
  billing/
    api/PaymentController.java
    api/dto/
      CreatePaymentRequest.java       amount, method, encounterId (opcional)
      VoidPaymentRequest.java         voidReason
      PaymentResponse.java            id, patientId, encounterId, amount, method, receiptNumber, paidAt, receivedBy,
                                      voided, voidReason
      AccountResponse.java            patientId, patientName, totalCharges, totalPayments, balance, sessions
      AccountSessionResponse.java     encounterId (puede ser null), date (OffsetDateTime), professionalName,
                                      treatments (List<String>, «<nombre> x<cantidad>»), charge, payment,
                                      runningBalance
    application/
      PaymentService.java             registrar, anular
      AccountService.java             arma el estado de cuenta (lo usa también ReceiptPdfService)
      ReceiptPdfService.java          genera el PDF con OpenPDF (seguir el patrón de
                                      prescriptions/application/PrescriptionPdfService.java sin importar esas clases;
                                      sin logo ni marca de agua: solo texto)
    domain/
      AccountBalance.java             reglas puras (sin Spring)
      ReceiptNumber.java              format(long sequenceValue) → "C-000001"
src/test/java/com/bellidental/billing/
  domain/AccountBalanceTest.java      (nuevo)
  PaymentFlowIT.java                  (nuevo)
docs/IMPLEMENTATION-PLAN.md           §4.8: cambiar «REVOKE DELETE (notas SQL)» por «trigger BEFORE DELETE (D-8c)»;
                                      §6 «Pendientes»: añadir D-8b y D-8c (provisionales)
```
`new PerformedTreatmentSummary(...)` solo aparece en `TreatmentDirectoryImpl` (verificado); `ClinicalSummaryService`
solo lo lee y no se toca.

## Criterios de aceptación
1. **V15** según `docs/schema.dbml` (`payments`, sección 10), exactamente:
   ```sql
   CREATE SEQUENCE payment_receipt_seq;
   CREATE TABLE payments (
       payment_id     uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
       patient_id     uuid          NOT NULL REFERENCES patients(patient_id),
       encounter_id   uuid          REFERENCES encounters(encounter_id),
       amount         decimal(10,2) NOT NULL CHECK (amount > 0),
       method         varchar(20)   NOT NULL CHECK (method IN ('EFECTIVO','TRANSFERENCIA','TARJETA')),
       receipt_number varchar(30)   UNIQUE,
       paid_at        timestamptz   NOT NULL DEFAULT now(),
       received_by    uuid          NOT NULL REFERENCES users(user_id),
       voided_at      timestamptz,
       void_reason    text
   );
   CREATE INDEX idx_payments_patient_id ON payments(patient_id);
   ```
   más una función PL/pgSQL `prevent_payment_delete()` y `CREATE TRIGGER trg_payments_no_delete BEFORE DELETE ON
   payments FOR EACH ROW EXECUTE FUNCTION prevent_payment_delete();` que hace
   `RAISE EXCEPTION 'Los pagos no se eliminan: se anulan con voided_at.'`.
2. `Payment` mapea la tabla (Lombok `@Getter @Setter @NoArgsConstructor`, `@Id @GeneratedValue(strategy =
   GenerationType.UUID) @Column(name = "payment_id") UUID id`, `patientId`/`encounterId`/`receivedBy` como `UUID`
   simples (sin relaciones), `amount` `BigDecimal`, `paidAt`/`voidedAt` `OffsetDateTime`, `method` con
   `@Enumerated(EnumType.STRING)`). `PaymentRepository extends JpaRepository<Payment, UUID>` con
   `@Query(value = "SELECT nextval('payment_receipt_seq')", nativeQuery = true) long nextReceiptSequence();` y
   `List<Payment> findByPatientIdAndVoidedAtIsNullOrderByPaidAtAsc(UUID patientId)`.
3. `POST /api/v1/patients/{patientId}/payments` (`BILLING_CREATE`) → 201 `ApiResponse<PaymentResponse>`:
   - paciente inexistente (`PatientDirectory.findById` vacío) → 404 `ResourceNotFoundException`;
   - `amount` `@NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2)`, `method` `@NotNull`;
   - si llega `encounterId`: `ClinicalDirectory.findEncounter` vacío → 404; si `patientId()` distinto o `voided()`
     → 409 `BusinessException("La atención no pertenece a este paciente.")`;
   - `receivedBy = currentUser.userId()` y `paidAt = clinicClock.now()` (`CurrentUser` y `ClinicClock` son beans:
     se inyectan por constructor, sus métodos no son estáticos), `receiptNumber =
     ReceiptNumber.format(paymentRepository.nextReceiptSequence())`.
4. `GET /api/v1/patients/{patientId}/account` (`BILLING_READ`) → `ApiResponse<AccountResponse>` (404 si el paciente
   no existe):
   - Atenciones: `ClinicalDirectory.findPatientEncounters(patientId)` (ya excluye anuladas, orden por `startAt`);
     tratamientos: `TreatmentDirectory.findPerformed(encounterIds)` (ya excluye anulados); cargo por atención =
     Σ `price × quantity`.
   - Pagos: `findByPatientIdAndVoidedAtIsNullOrderByPaidAtAsc`. Una **sesión** por atención: `date = startAt`,
     `professionalName` = nombre + apellido vía `ProfessionalDirectory.findByIds`, tratamientos, `charge`, `payment`
     = Σ de los pagos cuyo `encounterId` es esa atención.
   - Los pagos sin atención, **o ligados a una atención que no está en la lista (anulada)**, van en una sesión final
     con `encounterId = null`, `date` = `paidAt` del más reciente de ese grupo, `professionalName = null`,
     `treatments` vacía y `charge = 0`. Solo existe si hay pagos en ese grupo.
   - Sesiones en orden de fecha ascendente; `runningBalance` con `AccountBalance.runningBalances`.
     `totalCharges`, `totalPayments`, `balance = AccountBalance.balance(totalCharges, totalPayments)`.
     `patientName` = nombre + apellido. Sin diagnósticos.
5. `POST /api/v1/payments/{id}/void` (`BILLING_CREATE`), cuerpo `VoidPaymentRequest` con `voidReason` `@NotBlank`
   (y `@Valid @RequestBody`). Inexistente → 404; ya anulado → 409 `"El pago ya está anulado."`; si no →
   `voidedAt = clinicClock.now()`, `voidReason`. Devuelve 200 `ApiResponse<PaymentResponse>` con `voided = true`.
6. `GET /api/v1/payments/{id}/receipt` (`BILLING_READ`) → `byte[]` `application/pdf` con
   `Content-Disposition: inline; filename="<receiptNumber>.pdf"` (404 si no existe). Contenido, todo en español:
   nombre, dirección y teléfono de la clínica (`ClinicProperties.name()/address()/phone()`), «Comprobante interno —
   no válido como factura», «CONFIDENCIAL», número de comprobante, fecha del pago en la zona de la clínica,
   paciente (nombre y documento, de `PatientSummary`), método, monto, «Saldo pendiente actual» (el `balance` de
   `AccountService` al generar el PDF) y una línea de firma «Firma del paciente». Si el pago está anulado, añadir
   «ANULADO — <motivo>».
7. `AccountBalance` (puro, sin Spring ni repositorios; montos con escala 2 y `RoundingMode.HALF_UP`):
   - `static BigDecimal balance(BigDecimal totalCharges, BigDecimal totalPayments)` → cargos − abonos;
   - `static List<BigDecimal> runningBalances(List<BigDecimal> charges, List<BigDecimal> payments)` → listas de
     igual tamaño (una posición por sesión); devuelve el saldo acumulado en cada posición.
   `ReceiptNumber.format(long)` → `"C-%06d"`.
8. Controlador con `@PreAuthorize("hasAuthority('BILLING_CREATE')")` / `hasAuthority('BILLING_READ')`, como
   `PrescriptionController`. ODONTOLOGO no tiene estos permisos → 403. ADMIN sí (tiene todos).

## Tests requeridos
**Unit** (`AccountBalanceTest`, 3 casos):
- `balance_chargesAndPayments_returnsDifference` (100.00 − 30.00 → 70.00)
- `balance_paymentsExceedCharges_returnsNegative`
- `runningBalances_twoSessions_accumulates` (cargos [50, 20], abonos [10, 0] → [40.00, 60.00])

**Integration** (`PaymentFlowIT`, 3 casos). Setup como `PrescriptionFlowIT`/`EncounterFlowIT`: paciente nuevo
(documento con sufijo aleatorio), cita con check-in en `LocalDate.now().plusDays(101)` (día propio; otros IT usan
1–92 y 150), atención abierta por la odontóloga, `POST /encounters/{id}/treatments` con `price` 40.00 y `quantity`
1 (cargo X = 40.00), `POST /encounters/{id}/complete`. Cada test crea su propia cita en una hora distinta del mismo
día (o un día distinto: 101, 102, 103) para no chocar en el EXCLUDE de citas.
- `registerPaymentsAndReadAccount_returnsBalanceAndReceipt`: abonar 15.00 con SECRETARIA → 201 y `receiptNumber`
  que cumple `C-\d{6}`; `GET account` → `balance` = 25.00 y una sesión con `charge` 40.00; `GET receipt` → 200,
  `Content-Type` `application/pdf`, bytes que empiezan con `%PDF`.
- `voidPayment_restoresBalanceAndRequiresReason`: abonar 15.00; anular con `new VoidPaymentRequest(" ")` → 400
  (enviar siempre un cuerpo JSON: sin cuerpo el handler responde 500); anular con motivo → 200; `balance` vuelve a
  40.00; anular de nuevo → 409.
- `createPayment_asOdontologo_returnsForbidden`: 403.

## Decisiones provisionales (confirmar con el usuario)
- Métodos de pago: EFECTIVO, TRANSFERENCIA, TARJETA (los del esquema).
- Un pago puede exceder el saldo (queda a favor); no se bloquea.
- Anular pago lo puede hacer también SECRETARIA (D-8, ya tomada).
- D-8b y D-8c (arriba).

## Resultado

**Archivos creados**
- `src/main/resources/db/migration/V15__payments.sql` (secuencia, tabla, índice, función y trigger `BEFORE DELETE`).
- `shared/enums/PaymentMethod.java`, `persistence/entity/Payment.java`, `persistence/repository/PaymentRepository.java`.
- `billing/api/PaymentController.java`; `billing/api/dto/` (CreatePaymentRequest, VoidPaymentRequest, PaymentResponse, AccountResponse, AccountSessionResponse).
- `billing/application/` (PaymentService, AccountService, ReceiptPdfService); `billing/domain/` (AccountBalance, ReceiptNumber).
- Tests: `src/test/java/com/bellidental/billing/domain/AccountBalanceTest.java` (3), `src/test/java/com/bellidental/billing/PaymentFlowIT.java` (3).

**Archivos modificados**
- `treatments/PerformedTreatmentSummary.java` (añadido `BigDecimal price` al final) y `treatments/application/TreatmentDirectoryImpl.java` (pasa `pt.getPrice()`).
- `docs/IMPLEMENTATION-PLAN.md`: §4.8 (REVOKE DELETE → trigger BEFORE DELETE, D-8c) y §6 (filas D-8b y D-8c provisionales).

**Tests (resumen real)**
- Surefire: 95 tests, 0 fallos, 0 errores, 0 omitidos (incluye AccountBalanceTest 3/3).
- Failsafe: 66 tests, 0 fallos, 0 errores, 0 omitidos (incluye PaymentFlowIT 3/3).
- Comando: `./mvnw -q clean verify`, exit 0.

**Problemas y supuestos**
- Los permisos BILLING_CREATE/BILLING_READ ya existen en V4 (ADMIN todos; SECRETARIA ambos; ODONTOLOGO ninguno), no se tocó la semilla.
- En `PaymentFlowIT.registerPaymentsAndReadAccount` el abono se liga a la atención, así la cuenta tiene una sola sesión (cargo 40.00, saldo 25.00). El caso «pago sin atención» (sesión final sin cargo) está implementado pero no tiene test, según la lista de tests requeridos.
- El PDF del comprobante usa tamaño A5 y solo texto (sin logo ni marca de agua), con fuentes propias (no importa clases de prescriptions).
- Trigger creado con `CREATE FUNCTION` (sin `OR REPLACE`), ya que la función no existía.
- No se hizo commit.

## Revisión
Estado: APROBADO

- `./mvnw -q clean verify` exit 0. Surefire 95/0/0/0, failsafe 66/0/0/0 (incluye AccountBalanceTest 3 y PaymentFlowIT 3).
- V15 coincide con `schema.dbml` (NOT NULL, CHECK, secuencia, índice) más el trigger D-8c. `AccountBalance` es pura;
  `PaymentService` aplica 404/409 según los criterios; los pagos ligados a una atención anulada pasan a la sesión
  sin atención; los permisos BILLING_* están en el controlador.
- El archivo de la tarea quedó coherente: es la versión revisada y no hay instrucciones cruzadas.
