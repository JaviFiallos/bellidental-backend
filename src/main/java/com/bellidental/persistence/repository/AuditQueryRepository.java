package com.bellidental.persistence.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AuditQueryRepository {

    private static final Map<String, String> ENTITY_SUBQUERIES = Map.ofEntries(
            Map.entry("patients",
                    "SELECT 'patients' AS entity, x.patient_id::text AS entity_id, x.rev, x.revtype, x.patient_id "
                            + "FROM patients_aud x"),
            Map.entry("clinical-records",
                    "SELECT 'clinical-records' AS entity, x.clinical_record_id::text AS entity_id, x.rev, x.revtype, x.patient_id "
                            + "FROM clinical_records_aud x"),
            Map.entry("medical-history-entries",
                    "SELECT 'medical-history-entries' AS entity, x.medical_history_entry_id::text AS entity_id, x.rev, x.revtype, cr.patient_id "
                            + "FROM medical_history_entries_aud x "
                            + "LEFT JOIN clinical_records cr ON cr.clinical_record_id = x.clinical_record_id"),
            Map.entry("appointments",
                    "SELECT 'appointments' AS entity, x.appointment_id::text AS entity_id, x.rev, x.revtype, x.patient_id "
                            + "FROM appointments_aud x"),
            Map.entry("encounters",
                    "SELECT 'encounters' AS entity, x.encounter_id::text AS entity_id, x.rev, x.revtype, a.patient_id "
                            + "FROM encounters_aud x "
                            + "LEFT JOIN appointments a ON a.appointment_id = x.appointment_id"),
            Map.entry("encounter-diagnoses",
                    "SELECT 'encounter-diagnoses' AS entity, x.encounter_diagnosis_id::text AS entity_id, x.rev, x.revtype, a.patient_id "
                            + "FROM encounter_diagnoses_aud x "
                            + "LEFT JOIN encounters e ON e.encounter_id = x.encounter_id "
                            + "LEFT JOIN appointments a ON a.appointment_id = e.appointment_id"),
            Map.entry("dental-findings",
                    "SELECT 'dental-findings' AS entity, x.dental_finding_id::text AS entity_id, x.rev, x.revtype, a.patient_id "
                            + "FROM dental_findings_aud x "
                            + "LEFT JOIN encounters e ON e.encounter_id = x.encounter_id "
                            + "LEFT JOIN appointments a ON a.appointment_id = e.appointment_id"),
            Map.entry("performed-treatments",
                    "SELECT 'performed-treatments' AS entity, x.performed_treatment_id::text AS entity_id, x.rev, x.revtype, a.patient_id "
                            + "FROM performed_treatments_aud x "
                            + "LEFT JOIN encounters e ON e.encounter_id = x.encounter_id "
                            + "LEFT JOIN appointments a ON a.appointment_id = e.appointment_id"),
            Map.entry("prescriptions",
                    "SELECT 'prescriptions' AS entity, x.prescription_id::text AS entity_id, x.rev, x.revtype, a.patient_id "
                            + "FROM prescriptions_aud x "
                            + "LEFT JOIN encounters e ON e.encounter_id = x.encounter_id "
                            + "LEFT JOIN appointments a ON a.appointment_id = e.appointment_id"),
            Map.entry("prescription-items",
                    "SELECT 'prescription-items' AS entity, x.prescription_item_id::text AS entity_id, x.rev, x.revtype, a.patient_id "
                            + "FROM prescription_items_aud x "
                            + "LEFT JOIN prescriptions pr ON pr.prescription_id = x.prescription_id "
                            + "LEFT JOIN encounters e ON e.encounter_id = pr.encounter_id "
                            + "LEFT JOIN appointments a ON a.appointment_id = e.appointment_id"),
            Map.entry("payments",
                    "SELECT 'payments' AS entity, x.payment_id::text AS entity_id, x.rev, x.revtype, x.patient_id "
                            + "FROM payments_aud x"),
            Map.entry("treatments",
                    "SELECT 'treatments' AS entity, x.treatment_id::text AS entity_id, x.rev, x.revtype, NULL::uuid AS patient_id "
                            + "FROM treatment_catalog_aud x"),
            Map.entry("specialties",
                    "SELECT 'specialties' AS entity, x.specialty_id::text AS entity_id, x.rev, x.revtype, NULL::uuid AS patient_id "
                            + "FROM specialties_aud x")
    );

    /** Fila de la lista de auditoría; el módulo audit la convierte en su DTO. */
    public record AuditLogRow(Long revision, Instant at, UUID userId, String username, String entity,
                              String entityId, String type, UUID patientId, String patientName) {}

    private final EntityManager entityManager;

    public List<AuditLogRow> findAuditLog(
            OffsetDateTime start,
            OffsetDateTime end,
            String entity,
            UUID userId,
            UUID patientId,
            int offset,
            int size
    ) {
        String subquery = buildUnionSubquery(entity);
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT u.entity, u.entity_id, u.rev, r.revision_at, r.user_id, us.username, u.revtype, u.patient_id, ")
                .append("p.first_name || ' ' || p.last_name AS patient_name ")
                .append("FROM (").append(subquery).append(") u ")
                .append("JOIN revision_info r ON r.revision_id = u.rev ")
                .append("LEFT JOIN users us ON us.user_id = r.user_id ")
                .append("LEFT JOIN patients p ON p.patient_id = u.patient_id ")
                .append("WHERE r.revision_at >= :start AND r.revision_at < :end ");

        if (userId != null) {
            sql.append("AND r.user_id = :userId ");
        }
        if (patientId != null) {
            sql.append("AND u.patient_id = :patientId ");
        }

        sql.append("ORDER BY r.revision_at DESC, u.rev DESC, u.entity ASC ")
                .append("LIMIT :size OFFSET :offset");

        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("start", start);
        query.setParameter("end", end);
        query.setParameter("size", size);
        query.setParameter("offset", offset);
        if (userId != null) {
            query.setParameter("userId", userId);
        }
        if (patientId != null) {
            query.setParameter("patientId", patientId);
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<AuditLogRow> entries = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            entries.add(new AuditLogRow(
                    ((Number) row[2]).longValue(),
                    toInstant(row[3]),
                    toUuid(row[4]),
                    (String) row[5],
                    (String) row[0],
                    (String) row[1],
                    toRevType(row[6]),
                    toUuid(row[7]),
                    (String) row[8]
            ));
        }
        return entries;
    }

    public long countAuditLog(
            OffsetDateTime start,
            OffsetDateTime end,
            String entity,
            UUID userId,
            UUID patientId
    ) {
        String subquery = buildUnionSubquery(entity);
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) ")
                .append("FROM (").append(subquery).append(") u ")
                .append("JOIN revision_info r ON r.revision_id = u.rev ")
                .append("WHERE r.revision_at >= :start AND r.revision_at < :end ");

        if (userId != null) {
            sql.append("AND r.user_id = :userId ");
        }
        if (patientId != null) {
            sql.append("AND u.patient_id = :patientId ");
        }

        Query query = entityManager.createNativeQuery(sql.toString());
        query.setParameter("start", start);
        query.setParameter("end", end);
        if (userId != null) {
            query.setParameter("userId", userId);
        }
        if (patientId != null) {
            query.setParameter("patientId", patientId);
        }

        return ((Number) query.getSingleResult()).longValue();
    }

    public boolean existsEncounterAudit(UUID encounterId) {
        Query query = entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM encounters_aud WHERE encounter_id = :id");
        query.setParameter("id", encounterId);
        return ((Number) query.getSingleResult()).longValue() > 0;
    }

    public List<UUID> findEncounterDiagnosisIds(UUID encounterId) {
        Query query = entityManager.createNativeQuery(
                "SELECT DISTINCT encounter_diagnosis_id FROM encounter_diagnoses_aud WHERE encounter_id = :id");
        query.setParameter("id", encounterId);
        return toUuidList(query.getResultList());
    }

    public List<UUID> findDentalFindingIds(UUID encounterId) {
        Query query = entityManager.createNativeQuery(
                "SELECT DISTINCT dental_finding_id FROM dental_findings_aud WHERE encounter_id = :id");
        query.setParameter("id", encounterId);
        return toUuidList(query.getResultList());
    }

    public List<UUID> findPerformedTreatmentIds(UUID encounterId) {
        Query query = entityManager.createNativeQuery(
                "SELECT DISTINCT performed_treatment_id FROM performed_treatments_aud WHERE encounter_id = :id");
        query.setParameter("id", encounterId);
        return toUuidList(query.getResultList());
    }

    public List<UUID> findPrescriptionIds(UUID encounterId) {
        Query query = entityManager.createNativeQuery(
                "SELECT DISTINCT prescription_id FROM prescriptions_aud WHERE encounter_id = :id");
        query.setParameter("id", encounterId);
        return toUuidList(query.getResultList());
    }

    public List<UUID> findPrescriptionItemIds(List<UUID> prescriptionIds) {
        if (prescriptionIds == null || prescriptionIds.isEmpty()) {
            return List.of();
        }
        Query query = entityManager.createNativeQuery(
                "SELECT DISTINCT prescription_item_id FROM prescription_items_aud WHERE prescription_id IN (:prescriptionIds)");
        query.setParameter("prescriptionIds", prescriptionIds);
        return toUuidList(query.getResultList());
    }

    private String buildUnionSubquery(String entity) {
        if (entity != null) {
            String q = ENTITY_SUBQUERIES.get(entity);
            if (q != null) {
                return q;
            }
        }
        return String.join(" UNION ALL ", ENTITY_SUBQUERIES.values());
    }

    private static List<UUID> toUuidList(List<?> rows) {
        return rows.stream()
                .filter(Objects::nonNull)
                .map(AuditQueryRepository::toUuid)
                .toList();
    }

    private static UUID toUuid(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID u) {
            return u;
        }
        return UUID.fromString(value.toString());
    }

    private static Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof OffsetDateTime odt) {
            return odt.toInstant();
        }
        if (value instanceof Instant inst) {
            return inst;
        }
        return Instant.parse(value.toString());
    }

    private static String toRevType(Object value) {
        if (value == null) {
            return null;
        }
        int revtype = ((Number) value).intValue();
        return switch (revtype) {
            case 0 -> "ADD";
            case 1 -> "MOD";
            case 2 -> "DEL";
            default -> String.valueOf(revtype);
        };
    }
}
