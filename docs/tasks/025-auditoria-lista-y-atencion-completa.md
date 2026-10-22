# 025 — Auditoría: lista de cambios y historial completo de una atención (G-7 del frontend)

## Objetivo
Hoy solo existe `GET /audit/{entity}/{id}` (historial de **un** registro, tarea 013). El director no puede ver
«qué pasó ayer» ni quién cambió algo de un paciente sin conocer el id de cada fila, y el historial de una atención
no incluye sus diagnósticos, hallazgos, tratamientos ni recetas (son entidades aparte). Se añaden dos lecturas para
ADMIN, sin migración (las tablas `*_aud` y `revision_info` ya existen, V16/V17):

1. `GET /api/v1/audit?from&to&entity&userId&patientId&page&size` — **lista paginada** de revisiones de las 13
   entidades auditadas, una fila por (entidad, id, revisión), con el paciente al que pertenece.
2. `GET /api/v1/audit/encounters/{id}/full` — historial de la atención **y de sus hijos** (diagnósticos,
   hallazgos, tratamientos realizados, recetas y sus ítems), en una sola lista cronológica.

El detalle «antes → después» de cada fila sigue saliendo de `GET /audit/{entity}/{id}` (sin cambios de contrato,
solo dos campos añadidos al final, ver 4).

## Archivos a tocar
```
src/main/java/com/bellidental/
  persistence/repository/AuditQueryRepository.java       (nuevo) @Repository con EntityManager y SQL nativo de
                                                           solo lectura (como DashboardRepository)
  audit/
    api/AuditController.java                             (modif.) GET /audit y GET /audit/encounters/{id}/full
    api/dto/AuditLogEntryResponse.java                   (nuevo)
    api/dto/AuditEntryResponse.java                      (modif.) añadir al final: entity, entityId
    application/AuditService.java                        (modif.) findHistory rellena entity/entityId;
                                                           findEncounterFullHistory
    application/AuditLogService.java                     (nuevo) lista paginada
src/test/java/com/bellidental/audit/AuditLogIT.java      (nuevo)
docs/IMPLEMENTATION-PLAN.md                              §4.9 los dos endpoints nuevos
```
La ruta `/audit/encounters/{id}/full` no choca con `/{entity}/{id}` (tiene un segmento más). Declarar
`GET /audit` y `/encounters/{id}/full` antes del genérico por legibilidad.

## Criterios de aceptación
1. **Seguridad**: los dos endpoints con `@PreAuthorize("hasRole('ADMIN')")`. Respuestas con `ApiResponse`.
2. **`GET /audit`** → `ApiResponse<Page<AuditLogEntryResponse>>` (`new PageImpl<>(content, PageRequest.of(page,
   size), total)`, igual forma que `GET /patients`):
   - Parámetros: `from`, `to` (`LocalDate` ISO, opcionales; ambos o ninguno → si falta uno, 400
     `InvalidInputException`; por defecto **los últimos 7 días**: `to = clinicClock.today()`, `from = to − 6`);
     `from > to` → 400; rango > 92 días → 400. `entity` opcional (clave de `AUDITED_ENTITIES`; desconocida → 400);
     `userId` y `patientId` (`UUID`) opcionales; `page` (default 0, ≥ 0), `size` (default 50, 1–100; fuera → 400).
   - Rango en la zona de la clínica como en `DashboardService`: `start = from.atStartOfDay(zone)`,
     `end = to.plusDays(1).atStartOfDay(zone)`, filtro `r.revision_at >= :start AND r.revision_at < :end`.
   - `AuditLogEntryResponse(Long revision, Instant at, UUID userId, String username, String entity,
     String entityId, String type, UUID patientId, String patientName)`; `type` = `ADD`/`MOD`/`DEL` desde
     `revtype` 0/1/2; `patientName` = `first_name || ' ' || last_name` del paciente **actual** (null si la entidad
     no es de un paciente: tratamientos y especialidades).
   - Orden: `revision_at DESC, rev DESC, entity ASC`.
3. **SQL de la lista** (en `AuditQueryRepository`): una subconsulta por entidad unida con `UNION ALL` **solo con las
   entidades pedidas** (todas si `entity` es null), envuelta así:
   ```sql
   SELECT u.entity, u.entity_id, u.rev, r.revision_at, r.user_id, us.username, u.revtype, u.patient_id,
          p.first_name || ' ' || p.last_name AS patient_name
     FROM ( <subconsultas UNION ALL> ) u
     JOIN revision_info r ON r.revision_id = u.rev
     LEFT JOIN users us   ON us.user_id = r.user_id
     LEFT JOIN patients p ON p.patient_id = u.patient_id
    WHERE r.revision_at >= :start AND r.revision_at < :end
      [AND r.user_id = :userId] [AND u.patient_id = :patientId]
    ORDER BY r.revision_at DESC, u.rev DESC, u.entity ASC
    LIMIT :size OFFSET :offset
   ```
   y la misma consulta con `SELECT COUNT(*)` (sin `ORDER BY`/`LIMIT`) para el total. Subconsultas (columnas
   `entity, entity_id, rev, revtype, patient_id`; el paciente se toma de la fila **actual** de la tabla padre):
   | entity | subconsulta |
   |---|---|
   | `patients` | `SELECT 'patients', x.patient_id::text, x.rev, x.revtype, x.patient_id FROM patients_aud x` |
   | `clinical-records` | `… x.clinical_record_id::text …, x.patient_id FROM clinical_records_aud x` |
   | `medical-history-entries` | `… x.medical_history_entry_id::text …, cr.patient_id FROM medical_history_entries_aud x LEFT JOIN clinical_records cr ON cr.clinical_record_id = x.clinical_record_id` |
   | `appointments` | `… x.appointment_id::text …, x.patient_id FROM appointments_aud x` |
   | `encounters` | `… x.encounter_id::text …, a.patient_id FROM encounters_aud x LEFT JOIN appointments a ON a.appointment_id = x.appointment_id` |
   | `encounter-diagnoses` | `… x.encounter_diagnosis_id::text …` + `LEFT JOIN encounters e ON e.encounter_id = x.encounter_id LEFT JOIN appointments a ON a.appointment_id = e.appointment_id` → `a.patient_id` |
   | `dental-findings` | igual con `dental_findings_aud`, id `dental_finding_id` |
   | `performed-treatments` | igual con `performed_treatments_aud`, id `performed_treatment_id` |
   | `prescriptions` | igual con `prescriptions_aud`, id `prescription_id` |
   | `prescription-items` | `… x.prescription_item_id::text …` + `LEFT JOIN prescriptions pr ON pr.prescription_id = x.prescription_id` + encounters + appointments → `a.patient_id` |
   | `payments` | `… x.payment_id::text …, x.patient_id FROM payments_aud x` |
   | `treatments` | `… x.treatment_id::text …, NULL::uuid FROM treatment_catalog_aud x` |
   | `specialties` | `… x.specialty_id::text …, NULL::uuid FROM specialties_aud x` |
   Los textos SQL son constantes (un `Map<String, String>` en el repositorio con las mismas claves que
   `AUDITED_ENTITIES`); los valores del usuario van **siempre como parámetros**, nunca concatenados. Confirmar los
   nombres de columna contra V16/V17 antes de escribirlos.
4. `AuditEntryResponse` añade al final `String entity, String entityId`. `findHistory(entity, id)` los rellena con
   el `entity` pedido y el `id`. El contrato existente sigue igual (solo campos añadidos).
5. **`GET /audit/encounters/{id}/full`** → `ApiResponse<List<AuditEntryResponse>>`:
   - 404 si no hay ninguna revisión de esa atención en `encounters_aud`.
   - Ids de los hijos con SQL nativo en `AuditQueryRepository` (de las tablas `_aud`, así aparecen también los ya
     anulados): `SELECT DISTINCT encounter_diagnosis_id FROM encounter_diagnoses_aud WHERE encounter_id = :id`, y
     lo mismo en `dental_findings_aud`, `performed_treatments_aud`, `prescriptions_aud`; ítems:
     `SELECT DISTINCT prescription_item_id FROM prescription_items_aud WHERE prescription_id IN (:prescriptionIds)`
     (omitir si no hay recetas).
   - Por cada (entidad, id) llamar a `findHistory` y unir todo; orden por `at` ascendente y luego `revision`.
6. Sin escritura: `@Transactional(readOnly = true)` en ambos servicios.

## Tests requeridos
**Unit:** ninguno.

**Integration** (`AuditLogIT`, 3 casos; helpers de login y setup como `EncounterFlowIT`/`PaymentFlowIT`: paciente
nuevo con sufijo aleatorio, cita con check-in en `LocalDate.now().plusDays(121)` (día propio), atención abierta por
la odontóloga y un diagnóstico):
- `listAudit_byPatient_returnsEntriesOfPatientEncounterAndDiagnosis`: `GET /audit?patientId={id}` como director →
  200; el contenido incluye filas con `entity` `patients` (`type = ADD`), `encounters` y `encounter-diagnoses`,
  todas con `patientId` = el paciente y `patientName` informado; `totalElements` ≥ 3.
- `encounterFullHistory_includesDiagnosis`: `GET /audit/encounters/{encounterId}/full` como director → 200; hay al
  menos una entrada con `entity = "encounters"` y otra con `entity = "encounter-diagnoses"`; la lista está en orden
  ascendente de `at`.
- `listAudit_invalidOrForbidden_returnsErrors`: como secretaria `GET /audit` → 403; como director con
  `from` posterior a `to` → 400.

## Resultado
- **Qué se hizo**:
  - Creado `AuditQueryRepository` con subconsultas nativas `UNION ALL` para lista paginada y consultas nativas de solo lectura para las entidades hijas de una atención.
  - Creado el DTO `AuditLogEntryResponse` y añadidos los campos `entity` y `entityId` al final de `AuditEntryResponse`.
  - Creado `AuditLogService` con validaciones de fechas, rango de 92 días, páginas y tamaños, devolviendo `Page<AuditLogEntryResponse>`.
  - Actualizado `AuditService`: `findHistory` completa `entity` y `entityId`; implementado `findEncounterFullHistory` con validación 404 y unificación cronológica de la atención y sus hijos.
  - Actualizado `AuditController` exponiendo `GET /api/v1/audit` y `GET /api/v1/audit/encounters/{id}/full` protegidos con `@PreAuthorize("hasRole('ADMIN')")`.
  - Creado el test de integración `AuditLogIT` con los 3 casos requeridos.
  - Actualizado `docs/IMPLEMENTATION-PLAN.md` en §4.9.
- **Resultado de los tests**:
  - Surefire: Tests run: 103, Failures: 0, Errors: 0, Skipped: 0.
  - Failsafe: Tests run: 79, Failures: 0, Errors: 0, Skipped: 0 (incluyendo `AuditLogIT`: 3 tests).
- **Problemas encontrados y supuestos tomados**:
  - En `AuditLogIT`, para evitar violaciones de la restricción `appointments_no_overlap` entre ejecuciones sucesivas del `setUp()`, se asignó un día secuencial único para cada test (`AtomicInteger` a partir de `+121`).

## Revisión

Estado: APROBADO

Cumple los criterios 1–6: solo ADMIN, SQL constante por entidad con valores siempre como parámetros (sin inyección),
validaciones de rango/página → 400, historial completo de la atención con hijos ordenado por `at`.
Correcciones mínimas de Claude:
1. Arquitectura: `persistence/repository/AuditQueryRepository` importaba `audit.api.dto.AuditLogEntryResponse`
   (persistence no depende de los módulos). Ahora devuelve su propio record `AuditQueryRepository.AuditLogRow` y
   `AuditLogService` lo convierte al DTO.
2. Nombres calificados (clean-code): `AuditQueryRepository` y `java.util.Comparator` en `AuditService`,
   `java.sql.Timestamp` en el repositorio → `import`.
`./mvnw -q clean verify` (junto con la 024): surefire 103/0/0, failsafe 79/0/0.
