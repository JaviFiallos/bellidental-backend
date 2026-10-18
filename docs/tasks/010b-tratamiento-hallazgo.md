# 010b — Tratamiento ligado a un hallazgo del odontograma

## Objetivo
Completa la 010 (plan §4.6). Un tratamiento puede ligarse a un hallazgo del odontograma de la misma atención o de
una anterior del mismo paciente. Si el hallazgo está PENDING (rojo), se crea en la atención actual el hallazgo DONE
correspondiente (azul), y el odontograma del paciente lo muestra como realizado.

Se crea `OdontogramDirectory`, anunciada en la 009 para esta tarea.

Decisiones:
- `performed_treatments.dental_finding_id` guarda el hallazgo que eligió el odontólogo (el PENDING original), no el
  DONE creado.
- Si el hallazgo ya está DONE, solo se liga y no se crea otro.
- Anular el tratamiento no anula el DONE creado: el odontólogo lo anula en el odontograma si hace falta.
- `toothCode` y `surfaceCode` del tratamiento se copian del hallazgo cuando no llegan en la petición.

## Archivos a tocar
```
odontogram/
  OdontogramDirectory.java    (nuevo) Optional<FindingSummary> findFinding(UUID findingId);
                                      FindingSummary recordDoneFor(UUID findingId, UUID encounterId)
  FindingSummary.java         (nuevo) record: id, encounterId, toothCode, endToothCode, surfaceCode,
                                      conditionId, status, voided
  application/
    OdontogramDirectoryImpl.java   (nuevo) implementa la interfaz
    OdontogramService.java         extrae de createFinding la validación + guardado para reutilizarlos en
                                   recordDoneFor (sin duplicar FindingRules)
treatments/
  api/dto/RecordTreatmentRequest.java        + dentalFindingId
  api/dto/PerformedTreatmentResponse.java    + dentalFindingId
  application/TreatmentService.java          valida y liga el hallazgo
src/test/java/com/bellidental/treatments/TreatmentFlowIT.java   + 2 casos
```

## Criterios de aceptación
1. `recordDoneFor(findingId, encounterId)`: crea en `encounterId` un hallazgo con la misma pieza, pieza final,
   cara y condición, `status = DONE`, sin grado ni notas. Pasa por las mismas validaciones que `createFinding`
   (incluida la existencia del símbolo DONE para esa condición). Si la condición no tiene símbolo DONE → 409
   `"La condición del hallazgo no admite estado realizado."`.
2. `POST /api/v1/encounters/{id}/treatments` con `dentalFindingId`:
   - el hallazgo debe existir y no estar anulado (si no → 404 o 409 según corresponda);
   - su atención debe ser del mismo paciente que la atención actual; si no → 409
     `"El hallazgo no pertenece a este paciente."`;
   - si está PENDING → `recordDoneFor(hallazgo, atención actual)`, en la misma transacción que el tratamiento;
   - si está DONE → solo se liga.
3. `GET /api/v1/patients/{id}/odontogram` muestra la pieza o cara como DONE después de registrar el tratamiento.
4. `treatments` solo importa de `odontogram` la raíz (`OdontogramDirectory`, `FindingSummary`).

## Tests requeridos
**Integration** (`TreatmentFlowIT`, 2 casos nuevos; el total queda en 5):
- `recordTreatment_onPendingFindingFromPreviousEncounter_createsDoneFinding`: atención 1 con hallazgo PENDING
  (p. ej. caries en una cara) → atención 2 del mismo paciente → registrar tratamiento con ese `dentalFindingId` →
  201 con `dentalFindingId`; el odontograma del paciente muestra esa pieza y cara como DONE.
- `recordTreatment_findingFromOtherPatient_returnsConflict`: hallazgo de la atención de otro paciente → 409.

**Unit:** ninguno.

## Resultado
**Correcciones aplicadas (ronda 2)**
1. `odontogram/domain/DoneConditionResolver.java` (nuevo, regla pura): CARIES→FILLING, EXTRACTION→MISSING_CARIES,
   resto el mismo código. `recordDoneFor` resuelve la condición por código con `DentalConditionRepository.findByCode`
   (nuevo método, `code` es UNIQUE) y mantiene el 409 si esa condición no tiene símbolo DONE.
2. `recordDoneFor` copia `grade` y `notes` del hallazgo original (OTHER ya no da 400 por notas vacías).
3. `TreatmentService`: `linkDentalFinding` devuelve el `FindingSummary`; si la petición no trae `toothCode` o
   `surfaceCode`, se copian del hallazgo ligado (`toothCodeOf` / `surfaceCodeOf`).
4. `TreatmentFlowIT.recordTreatment_onPendingFindingFromPreviousEncounter_createsDoneFinding` usa CARIES en la cara M.
   Verifica que la respuesta trae `toothCode` 16 y `surfaceCode` "M" copiados, y que el odontograma muestra una sola
   entrada en pieza 16 / cara M con status DONE y conditionId de FILLING.
5. `odontogram/domain/DoneConditionResolverTest.java` (nuevo, 2 casos: `resolveDoneConditionCode_caries_returnsFilling`,
   `resolveDoneConditionCode_sealant_returnsSameCode`).
6. `docs/IMPLEMENTATION-PLAN.md` §6 Pendientes: fila D-14c añadida.

**Archivos de la tarea (todos los cambios de la ronda 1 siguen vigentes)**
- Nuevos: `odontogram/OdontogramDirectory.java`, `odontogram/FindingSummary.java`,
  `odontogram/application/OdontogramDirectoryImpl.java`, `odontogram/domain/DoneConditionResolver.java`,
  `src/test/java/com/bellidental/odontogram/domain/DoneConditionResolverTest.java`.
- Modificados: `odontogram/application/OdontogramService.java`, `treatments/application/TreatmentService.java`,
  `treatments/api/dto/RecordTreatmentRequest.java` (+dentalFindingId), `treatments/api/dto/PerformedTreatmentResponse.java`
  (+dentalFindingId), `src/test/java/com/bellidental/treatments/TreatmentFlowIT.java`, `docs/IMPLEMENTATION-PLAN.md`.
- Aceptado por la revisión: `persistence/entity/PerformedTreatment.java` (campo `dentalFindingId`).
- Fuera de la lista de la tarea, pero necesario: `persistence/repository/DentalConditionRepository.java`
  (`Optional<DentalCondition> findByCode(String code)`).

**Tests** (`./mvnw -q clean verify`, una vez)
- Surefire: 85 run, 0 failures, 0 errors, 0 skipped.
- Failsafe: 41 run, 1 failure, 0 errors, 0 skipped. El fallo es
  `AvailabilityIT.walkIn_noAppointmentsToday_createsWalkInCheckedIn` (409 "Sin hueco hoy para este profesional."). No se
  tocó esa clase. Depende de la hora: con el reloj actual (23:36 hora local) el único hueco que queda hasta las 23:59
  es menor que la duración por defecto. Pasó en la ejecución completa anterior y falla también al correrlo solo.
  Requiere un arreglo aparte (fecha/hora fija en el test o tolerancia al cierre del día).
- De esta tarea: `TreatmentFlowIT` 5/5, `OdontogramFlowIT` 3/3, `DoneConditionResolverTest` 2/2 (corridos en la
  ejecución de desarrollo y en la completa).

**Problemas y supuestos**
- El build incremental volvió a dejar "Unresolved compilation problems" en `target/`; se resolvió con `./mvnw -q clean`.
- Sin commit.

## Revisión
Estado: CORRECCIONES

Se acepta el cambio en `persistence/entity/PerformedTreatment.java` (`dentalFindingId`): es necesario y faltaba en la
lista. El resto está bien estructurado (Directory en la raíz, `saveFinding` compartido, sin ciclos). Falta:

1. **Condición del DONE (error de diseño de la tarea, detectado por el ejecutor):** CARIES y EXTRACTION solo tienen
   símbolo PENDING, así que los dos casos más comunes (obturar una caries, extraer una pieza) dan 409. Crear la
   regla pura `odontogram/domain/DoneConditionResolver.java` con `String resolveDoneConditionCode(String pendingCode)`:
   `CARIES → FILLING`, `EXTRACTION → MISSING_CARIES`, cualquier otro → el mismo código. `recordDoneFor` busca la
   condición resuelta por código (`DentalConditionRepository`) y crea el DONE con ella. Si la condición resuelta no
   tiene símbolo DONE, el 409 actual se mantiene. Constantes con nombre para los códigos, sin cadenas sueltas.
2. **Grado y notas:** `recordDoneFor` copia `grade` y `notes` del hallazgo original en vez de pasar `null` (OTHER
   exige notas). Con eso el DONE de OTHER no da 400.
3. **Pieza y cara (decisión de la tarea no implementada):** en `TreatmentService`, si la petición no trae
   `toothCode` o `surfaceCode`, se copian del hallazgo ligado. `linkDentalFinding` puede devolver el
   `FindingSummary` para usarlo al construir la entidad.
4. **Test IT:** el caso `recordTreatment_onPendingFindingFromPreviousEncounter_createsDoneFinding` pasa a usar CARIES
   en una cara (como decía la tarea): el odontograma del paciente muestra FILLING DONE en esa pieza y cara, y la
   respuesta del tratamiento trae `toothCode` y `surfaceCode` copiados del hallazgo (la petición no los envía).
5. **Unit** (`odontogram/domain/DoneConditionResolverTest`, 2 casos, autorizados por esta revisión):
   `resolveDoneConditionCode_caries_returnsFilling` y `resolveDoneConditionCode_sealant_returnsSameCode`.
6. **Plan:** en `docs/IMPLEMENTATION-PLAN.md` §6 «Pendientes», añadir `D-14c`: «Odontóloga: ¿caries tratada =
   obturación (FILLING) y extracción indicada realizada = perdido por caries (MISSING_CARIES)?» · propuesta: sí ·
   afecta: 010b. Archivo nuevo para la tarea: se autoriza tocarlo.

### Ronda 2
Estado: APROBADO

Correcciones 1–6 aplicadas. Se aceptan los cambios en `PerformedTreatment` y `DentalConditionRepository.findByCode`.
Corrección mínima de Claude: `DoneConditionResolver` pasa a `final` con constructor privado. `DoneConditionResolverTest`
2/2, `TreatmentFlowIT` 5/5, `OdontogramFlowIT` 3/3.

Aparte, no es de esta tarea: `AvailabilityIT.walkIn_noAppointmentsToday_createsWalkInCheckedIn` usa la hora real y falla
entre las 23:30 y las 24:00 locales (no queda hueco antes de medianoche). Pendiente de una tarea de corrección:
fijar el reloj en ese test.
