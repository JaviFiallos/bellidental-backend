# 004a — Tipo de sangre: guardar la etiqueta en `varchar(3)` (error 500 al crear un paciente)

> Corrección sobre la 004. La encontró el usuario probando el alta de paciente desde recepción (frontend 005a).

## Objetivo
`POST /api/v1/patients` con `bloodType` (por ejemplo `"O_POS"`) responde **500**:
```
ERROR: value too long for type character varying(3)   (SQLState 22001)
```
**Causa:**
- `patients.blood_type` es `varchar(3)` (V3 y `schema.dbml`), pensado para la etiqueta clínica: `O+`, `AB-`…
- La entidad `Patient` lo mapea con `@Enumerated(EnumType.STRING)`, que guarda el **nombre** del enum: `O_POS`
  (5 caracteres) o `AB_NEG` (6).
- Ningún test creaba pacientes con tipo de sangre, así que el error no salió antes.

**Corrección:** guardar la etiqueta (`O+`, `AB-`) con un `AttributeConverter`, sin migración y sin cambiar el
contrato de la API.
- El JSON sigue usando el nombre del enum en `bloodType` (`"O_POS"`), más `bloodTypeLabel` (`"O+"`) en la respuesta.
- La columna de 3 caracteres alcanza para todas las etiquetas.
- No se usa V9, porque está reservada para la tarea 010 (tratamientos).

## Archivos a tocar
```
shared/enums/BloodType.java                    + static BloodType fromLabel(String label) (IllegalArgumentException si no existe)
persistence/entity/BloodTypeConverter.java     (nuevo) AttributeConverter<BloodType, String>: enum ↔ etiqueta; null ↔ null
persistence/entity/Patient.java                bloodType: @Convert(converter = BloodTypeConverter.class) en lugar de @Enumerated
src/test/java/com/bellidental/patients/
  PatientBloodTypeIT.java                      (nuevo)
```
No cambian `CreatePatientRequest`, `UpdatePatientRequest`, `PatientResponse`, `PatientService` ni ningún
`Directory`.

## Criterios de aceptación
1. **Alta:** `POST /api/v1/patients` con cualquiera de los 8 tipos (`O_POS`…`AB_NEG`) → `201`, con
   `data.bloodType` igual al nombre enviado y `data.bloodTypeLabel` con la etiqueta (`"AB-"`).
2. **En la base:** la columna `blood_type` guarda la etiqueta (`O+`, `AB-`), no el nombre del enum.
3. **Lectura y edición:** `GET /api/v1/patients/{id}` y `PUT /api/v1/patients/{id}` (cambiar el tipo) devuelven el
   valor correcto.
4. **Sin tipo de sangre:** sigue guardando `null`, como hoy.
5. **Etiqueta inválida en la base:** si `blood_type` tiene un valor que no corresponde a ninguna etiqueta, el
   converter lanza `IllegalArgumentException` con un mensaje claro. No devuelve `null` en silencio.
6. **Plan:** fila `| 004a | Tipo de sangre como etiqueta (bug 500 en alta) | — | 004 | PatientBloodTypeIT |` en
   §3, debajo de la 004.

## Tests requeridos
- **Unit:** ninguno. El converter es trivial y lo cubre el IT.
- **Integration:** `PatientBloodTypeIT`, con `TestcontainersConfiguration` y la sesión de secretaria de la semilla.
  2 casos:
  1. `createPatient_withLongestBloodType_storesLabelAndReturnsEnum`:
     - `POST` con `bloodType: "AB_NEG"` → `201`, `data.bloodType == "AB_NEG"` y `data.bloodTypeLabel == "AB-"`;
     - con `JdbcTemplate`, `select blood_type from patients where patient_id = ?` devuelve `"AB-"`.
  2. `updatePatient_changesBloodType`:
     - crear con `O_POS`;
     - `PUT` con `bloodType: "B_NEG"` → `data.bloodType == "B_NEG"`;
     - `GET` devuelve lo mismo.
- **Deben seguir pasando** todos los IT y unit tests actuales (116).

## Resultado

**Archivos:**
- `src/main/java/com/bellidental/shared/enums/BloodType.java` (modificado): `static BloodType fromLabel(String label)`; recorre `values()` y, si no hay coincidencia, lanza `IllegalArgumentException("Etiqueta de tipo de sangre no válida: <label>")`.
- `src/main/java/com/bellidental/persistence/entity/BloodTypeConverter.java` (nuevo): `@Converter`, `AttributeConverter<BloodType, String>`; enum → `getLabel()`, etiqueta → `fromLabel`, `null` ↔ `null`.
- `src/main/java/com/bellidental/persistence/entity/Patient.java` (modificado): `@Enumerated(EnumType.STRING)` reemplazado por `@Convert(converter = BloodTypeConverter.class)`; `@Column(name = "blood_type", length = 3)` se mantiene.
- `src/test/java/com/bellidental/patients/PatientBloodTypeIT.java` (nuevo): 2 casos del `## Tests requeridos`, con login de secretaria, `TestRestTemplate` y `JdbcTemplate` (sin cambios en DTOs, servicios ni API).
- `docs/IMPLEMENTATION-PLAN.md` (modificado): fila `| 004a | Tipo de sangre como etiqueta (bug 500 en alta) | — | 004 | PatientBloodTypeIT |` debajo de la 004 en §3.

Sin migraciones. No se tocaron `proy-infor/`, el frontend ni otros módulos. No se hizo commit.

**Tests (por pedido del usuario solo se ejecutó `PatientBloodTypeIT`, no la suite completa):**
Comando: `./mvnw -q verify -Dit.test=PatientBloodTypeIT -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false` (exit code 0).
Failsafe (`target/failsafe-reports/com.bellidental.patients.PatientBloodTypeIT.txt`):
`Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 115.3 s -- in com.bellidental.patients.PatientBloodTypeIT`

Docker estaba disponible (contenedor `postgres:16-alpine` levantado por Testcontainers).

**Problemas y supuestos:**
- El `mvnw` se ejecutó con `-q`, así que no hay línea de surefire: el resumen proviene del report de failsafe.
- El log completo de Maven se guardó en `/tmp/claude-1000/bt-run.log` (fuera del scratchpad; solo local, no forma parte del proyecto).
- `fromLabel` lanza `IllegalArgumentException`, tal como pide la tarea. La convención de `clean-code` evita esa excepción genérica hacia el handler; el converter solo se invoca al leer de la base, así que no llega a la API como 4xx.
- La verificación de la columna en la base se hace con `JdbcTemplate` sobre el caso de alta (`"AB-"`). El caso de etiqueta inválida (criterio 5) no tiene test, porque la tarea lista solo 2 casos.

## Revisión
**Estado: APROBADO** (revisión 1, Claude Opus)

> La ejecutó **Haiku** a pedido del usuario: Gemini se quedó sin cuota. Por pedido del usuario solo se corrió el test de
> la tarea, no la suite completa.

Verificado por Claude: `./mvnw -q verify -Dit.test=PatientBloodTypeIT -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false`
→ exit 0; failsafe `PatientBloodTypeIT` 2/2.
- **Criterios 1 a 4:** `BloodTypeConverter` guarda la etiqueta (`AB-`) y lee con `fromLabel`. `Patient` cambia
  `@Enumerated` por `@Convert` y mantiene `varchar(3)`. No cambiaron los DTO, el servicio ni la API.
- **Criterio 5:** `fromLabel` lanza `IllegalArgumentException` con la etiqueta. Solo se invoca al leer de la base,
  así que no llega a la API como 4xx.
- **Criterio 6:** el plan tiene la fila 004a en §3.

Pendiente: la suite completa (`./mvnw -q clean verify`) no se corrió en esta tarea, por pedido del usuario. Se
recomienda correrla antes del próximo commit grande del backend. El cambio es acotado (una entidad y un converter),
y los demás IT crean pacientes sin tipo de sangre (`null`), que el converter respeta.

Commit (cuando el usuario lo autorice): `shared/enums/BloodType.java`, `persistence/entity/{Patient,BloodTypeConverter}.java`,
`PatientBloodTypeIT.java`, `docs/IMPLEMENTATION-PLAN.md` y este archivo. No incluir `proy-infor/`.
