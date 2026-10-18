# 011b — PDF de la receta

## Objetivo
Completa la 011 (plan §4.7). Una receta emitida (ISSUED) se descarga en PDF para imprimirla, firmarla y sellarla.
Se generan con OpenPDF (§2 del plan). Los datos de la clínica salen de `application.clinic.*`, y el comprobante de la
012 los reutilizará.

## Archivos a tocar
```
pom.xml                                   + com.github.librepdf:openpdf (2.0.3)
src/main/resources/application.yml        + application.clinic.name, address, phone (valores provisionales)
config/ClinicProperties.java              (nuevo) @ConfigurationProperties("application.clinic"): name, address, phone
professionals/                            (cambio mínimo)
  ProfessionalSummary.java                + licenseNumber
  ProfessionalDirectoryImpl.java          lo mapea (y cualquier otro sitio que construya el record)
prescriptions/
  api/PrescriptionController.java         + GET /api/v1/prescriptions/{id}/pdf
  application/PrescriptionPdfService.java (nuevo) arma el documento con OpenPDF y devuelve byte[]
src/test/java/com/bellidental/prescriptions/PrescriptionPdfIT.java   (nuevo)
```

## Criterios de aceptación
1. `GET /api/v1/prescriptions/{id}/pdf` (RECORD_READ): solo recetas ISSUED no anuladas; si no → 409
   `"Solo se puede imprimir una receta emitida."`. Responde `200`, `Content-Type: application/pdf` y
   `Content-Disposition: inline; filename="receta-<id>.pdf"`. Sin envolver en `ApiResponse` (es un binario).
2. Contenido, en este orden:
   - cabecera: nombre, dirección y teléfono de la clínica, y la leyenda **CONFIDENCIAL**;
   - paciente: nombres y apellidos, cédula, edad (en años, a la fecha de emisión en `application.clinic.zone`);
   - fecha de emisión (`prescribed_at` en la zona de la clínica, `dd/MM/yyyy`);
   - diagnóstico: código CIE-10 + descripción;
   - ítems numerados: medicamento (nombre, concentración, presentación), dosis, frecuencia, duración, cantidad e
     indicaciones;
   - indicaciones generales (si hay);
   - pie: profesional (nombres y apellidos), «Reg. <licenseNumber>», línea de firma y «Firma y sello».
3. Datos externos solo por interfaces públicas: `ClinicalDirectory` (atención → paciente y profesional,
   diagnóstico), `PatientDirectory`, `ProfessionalDirectory`. Nada de repositorios de otros módulos para eso.
4. `PrescriptionPdfService` no conoce HTTP: recibe el id y devuelve `byte[]`; el controlador arma las cabeceras.

## Tests requeridos
**Integration** (`PrescriptionPdfIT`, 2 casos; setup como `PrescriptionFlowIT`):
- `downloadPdf_issuedPrescription_returnsPdfWithConfidentialAndPatientDocument`: receta emitida → 200,
  `application/pdf`, los bytes empiezan por `%PDF`, y el texto extraído (`PdfTextExtractor` de OpenPDF) contiene
  «CONFIDENCIAL» y la cédula del paciente.
- `downloadPdf_draftPrescription_returnsConflict`: receta en DRAFT → 409.

**Unit:** ninguno.

## Resultado
**Archivos creados**
- `src/main/java/com/bellidental/config/ClinicProperties.java` (record `@ConfigurationProperties("application.clinic")`: name, address, phone).
- `src/main/java/com/bellidental/prescriptions/application/PrescriptionPdfService.java` (`generatePdf(UUID) -> byte[]`, OpenPDF).
- `src/test/java/com/bellidental/prescriptions/PrescriptionPdfIT.java` (2 casos).

**Archivos modificados**
- `pom.xml`: `com.github.librepdf:openpdf:2.0.3` (resuelve sin problemas).
- `src/main/resources/application.yml`: `application.clinic.name/address/phone` con valores provisionales.
- `src/main/java/com/bellidental/BellidentalBackendApplication.java`: `@ConfigurationPropertiesScan` para habilitar `ClinicProperties`.
- `professionals/ProfessionalSummary.java`: `+ String licenseNumber` (último componente). `ProfessionalDirectoryImpl.java` lo mapea.
- `prescriptions/api/PrescriptionController.java`: `GET /api/v1/prescriptions/{id}/pdf` (`RECORD_READ`), `ResponseEntity<byte[]>`, `Content-Type: application/pdf`, `Content-Disposition: inline; filename="receta-<id>.pdf"`.

**Extra no listado (necesario para compilar):**
- `src/test/java/com/bellidental/appointments/application/ScheduleServiceTest.java`: 2 llamadas a `new ProfessionalSummary(...)` reciben el argumento `null` extra de `licenseNumber`.

**Decisiones y supuestos**
- `PrescriptionPdfService` no usa repositorios ni `PatientDirectory` de otros módulos para la receta: reutiliza `PrescriptionService.findById` (DTO con ítems, diagnóstico y estado) y luego `ClinicalDirectory.findEncounter`, `PatientDirectory.findById`, `ProfessionalDirectory.findById`.
- Regla de impresión: `status == ISSUED && !voided`, si no `BusinessException` → 409 con el mensaje exacto. Se dejó en el servicio (no se tocó `PrescriptionRules`, que no está en la lista).
- La etiqueta es «Cédula:» con `documentNumber` aunque el tipo sea pasaporte (el paciente del IT es PASAPORTE). Así lo pide el criterio; si se quiere etiqueta según `documentType`, es un cambio menor.
- Fallo de OpenPDF se envuelve en `IllegalStateException` (500 genérico).
- Valores provisionales de la clínica: `address: Dirección por definir`, `phone: "0000000000"`, `name: Bellidental Clínica Dental`.
- `PrescriptionPdfIT` usa el día `now+92` (el de `PrescriptionFlowIT` es `now+90`) para no chocar con sus citas.

**Tests**
- Desarrollo: `PrescriptionPdfIT` 2/2 OK.
- `./mvnw -q clean verify` (una vez):
  - Surefire: 89 run, 0 failures, 0 errors, 0 skipped.
  - Failsafe: 46 run, 1 failure, 0 errors, 0 skipped. Único fallo: `AvailabilityIT.walkIn_noAppointmentsToday_createsWalkInCheckedIn`, el flake conocido (la hora local en la ejecución era 23:55).
  - `PrescriptionPdfIT`: 2 run, 0 failures.
- No se hizo commit.

## Revisión
Estado: APROBADO

Cumple los criterios 1–4: datos solo vía `ClinicalDirectory`, `PatientDirectory` y `ProfessionalDirectory`; el
servicio devuelve `byte[]` y el controlador arma las cabeceras. Se aceptan los extras declarados
(`@ConfigurationPropertiesScan`, `ScheduleServiceTest`). Correcciones mínimas de Claude en `PrescriptionPdfService`:
la cantidad se omite si no llega (antes imprimía «Cantidad: null») y la etiqueta es «Cédula» solo para CI, si no
«Documento». `./mvnw -q clean verify` a las 00:00: surefire 89/0, failsafe 46/0.
