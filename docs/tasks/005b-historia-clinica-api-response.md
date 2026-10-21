# 005b — Historia clínica: respuestas con ApiResponse (G-9 del frontend)

> Corrección pequeña sobre la 005. Es el mismo problema que la 006c resolvió para los horarios. Bloquea la tarea 008b
> del frontend (panel de antecedentes) y hoy rompe la cabecera clínica de la 008a.

## Objetivo
`ClinicalRecordController` es el único controlador que todavía devuelve los DTO sin el envoltorio estándar
`ApiResponse<T> { success, message, data }` (plan §2). El `ApiClient` del frontend desenvuelve `data` en todas las
respuestas, así que la pantalla de atención recibe `undefined` en lugar de la historia clínica.

Hay que envolver sus 6 endpoints sin cambiar rutas, permisos ni reglas. Además, la medicación actual tiene que
poder borrarse: hoy un `""` se guarda como cadena vacía.

## Archivos a tocar
```
clinicalrecords/
  ClinicalRecordController.java     los 6 endpoints devuelven ResponseEntity<ApiResponse<…>>
  ClinicalRecordService.java        update: currentMedication con trim; vacía → null (borra)
src/test/java/com/bellidental/clinicalrecords/
  ClinicalRecordApiIT.java          (nuevo)
src/test/java/com/bellidental/clinical/
  EncounterFlowIT.java              solo si lee el JSON de clinical-record sin el envoltorio: leer data.…
docs/IMPLEMENTATION-PLAN.md         §3 (fila 005b) y §4.2 (envoltorio y regla de currentMedication)
```

## Criterios de aceptación
1. `POST /api/v1/patients/{patientId}/clinical-record` → `201` `ApiResponse<ClinicalRecordResponse>`, con el
   message "Historia clínica abierta.". El `201` pasa de `@ResponseStatus` a `ResponseEntity.status(HttpStatus.CREATED)`,
   como en la 006c.
2. `GET /api/v1/patients/{patientId}/clinical-record?includeVoided` → `200` `ApiResponse<ClinicalRecordResponse>`.
3. `PUT /api/v1/clinical-records/{id}` → `200` `ApiResponse<ClinicalRecordResponse>`, con el message
   "Historia clínica actualizada.".
4. `GET /api/v1/medical-history-conditions` → `200` `ApiResponse<List<MedicalHistoryConditionResponse>>`.
5. `POST /api/v1/clinical-records/{id}/history-entries` → `201` `ApiResponse<MedicalHistoryEntryResponse>`, con el
   message "Antecedente registrado.".
6. `POST /api/v1/history-entries/{id}/void` → `200` `ApiResponse<Void>`, con el message "Antecedente anulado.".
7. **`currentMedication` en el `PUT`:**
   - si viene `null` o ausente, no cambia (como hoy);
   - si trae texto, se guarda recortado (trim);
   - si es vacía o solo espacios, se guarda como `null`, es decir, se borra.
8. **Lo demás no cambia:** los permisos (`RECORD_CREATE`, `RECORD_READ`, `RECORD_UPDATE`) y las reglas del
   servicio:
   - `noKnownHistory = true` con antecedentes activos → 409;
   - `FAMILY` exige `relative` y `PERSONAL` no lo admite → 400;
   - la condición "Otro" (id 10) exige `description` → 400;
   - registrar un antecedente desmarca `noKnownHistory`.

   Los errores siguen pasando por el manejador global.
9. **Plan:**
   - §3: fila `| 005b | Historia clínica con ApiResponse (G-9 del frontend) | — | 005 | ClinicalRecordApiIT |`, debajo
     de la 005;
   - §4.2: una línea que diga que todos los endpoints responden con `ApiResponse` y la regla 7.

## Tests requeridos
- **Unit:** ninguno. `ClinicalRecordServiceTest` debe seguir pasando; si algún caso verifica que `""` se guarda, se
  ajusta a la regla 7.
- **Integration:** `ClinicalRecordApiIT`, con `TestcontainersConfiguration`, datos por API y la sesión de odontóloga
  de la semilla. 2 casos:
  1. `clinicalRecord_wrappedRoundTrip`:
     - crear un paciente;
     - `POST …/clinical-record` → `201` con `success`, `data.recordNumber` y `data.historyEntries == []`;
     - `GET /medical-history-conditions` → `data` con 10 condiciones y `data[9].name == "Otro"`;
     - `POST …/history-entries` (Diabetes, `PERSONAL`) → `201` con `data.conditionName == "Diabetes"`;
     - `GET …/clinical-record` → `data.historyEntries[0].active == true`;
     - `POST /history-entries/{id}/void` → `200` con el message "Antecedente anulado.".
  2. `updateClinicalRecord_medicationClearsAndNoKnownHistoryConflict`:
     - `PUT` con `currentMedication: "  metformina  "` → `data.currentMedication == "metformina"`;
     - `PUT` con `currentMedication: ""` → `data.currentMedication == null`;
     - registrar un antecedente activo y hacer `PUT` con `noKnownHistory: true` → `409` con el message del servicio.
- **Deben seguir pasando:** `EncounterFlowIT` y `OdontogramFlowIT`.

## Resultado
- **Qué se hizo:**
  - `clinicalrecords/ClinicalRecordController.java`: se envolvieron los 6 endpoints en `ResponseEntity<ApiResponse<...>>`, cambiando `openClinicalRecord` y `addHistoryEntry` a `ResponseEntity.status(HttpStatus.CREATED)` con sus respectivos mensajes, y retornando `ApiResponse<Void>` con "Antecedente anulado." en `voidHistoryEntry`.
  - `clinicalrecords/ClinicalRecordService.java`: en `update`, se agregó el trim sobre `currentMedication` y conversión de cadena vacía/espacios a `null` para permitir su borrado.
  - `src/test/java/com/bellidental/clinicalrecords/ClinicalRecordApiIT.java`: se creó el test de integración con los 2 casos requeridos (`clinicalRecord_wrappedRoundTrip` y `updateClinicalRecord_medicationClearsAndNoKnownHistoryConflict`).
  - `docs/IMPLEMENTATION-PLAN.md`: se documentó la fila 005b en §3 y el uso de `ApiResponse` junto con la regla de `currentMedication` en §4.2.
- **Resultado de los tests:**
  - Surefire: Tests run: 83, Failures: 0, Errors: 0, Skipped: 0.
  - Failsafe: Tests run: 33, Failures: 0, Errors: 0, Skipped: 0.
  - Total: 116 tests pasaron exitosamente en `./mvnw -q clean verify`.
- **Problemas encontrados y supuestos tomados:**
  - `EncounterFlowIT` ya esperaba `ApiResponse<ClinicalRecordResponse>` en su verificación de historia clínica, por lo que siguió pasando sin modificaciones al igual que `OdontogramFlowIT`.

## Revisión

**Estado: APROBADO** (revisión 1, Claude)

Verificado por Claude con `./mvnw -q clean verify`: surefire 83/83 y failsafe 33/33 (`ClinicalRecordApiIT` 2/2).
- Criterios 1 a 9 cumplidos.
- Los 6 endpoints responden con `ApiResponse`; los dos `201` pasaron a `ResponseEntity.status(HttpStatus.CREATED)`.
- `currentMedication` se guarda recortada, y vacía pasa a `null`; si viene `null`, no cambia.
- No cambiaron los permisos ni las reglas del servicio.
- `EncounterFlowIT` ya leía la historia clínica con el envoltorio, así que no hubo que tocarlo.
- El plan quedó actualizado en §3 y §4.2.

Commit: el código y los tests de la tarea, `docs/IMPLEMENTATION-PLAN.md` y este archivo. No incluir `proy-infor/`.
