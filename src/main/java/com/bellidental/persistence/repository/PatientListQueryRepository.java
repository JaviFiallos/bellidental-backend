package com.bellidental.persistence.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Consulta nativa paginada para la lista informativa de pacientes con agregados de última atención y saldo.
 */
@Repository
@RequiredArgsConstructor
public class PatientListQueryRepository {

    private static final String BALANCE_EXPRESSION = "COALESCE(ch.total_charges, 0) - COALESCE(pm.total_payments, 0)";

    public record PatientListRow(
            UUID id,
            String firstName,
            String lastName,
            String documentType,
            String documentNumber,
            LocalDate birthDate,
            OffsetDateTime lastEncounterAt,
            OffsetDateTime createdAt,
            BigDecimal pendingBalance
    ) {}

    private final EntityManager entityManager;

    public List<PatientListRow> findPatientList(
            boolean isAttendedView,
            OffsetDateTime start,
            OffsetDateTime end,
            boolean withBalance,
            boolean includeBalance,
            String numericQuery,
            String phonePrefix,
            String textQueryPattern,
            int offset,
            int size,
            ZoneId zone
    ) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT p.patient_id, p.first_name, p.last_name, p.document_type, p.document_number, p.birth_date, ")
                .append("enc.last_encounter_at, p.created_at, ");

        if (includeBalance) {
            sql.append("(").append(BALANCE_EXPRESSION).append(") AS pending_balance ");
        } else {
            sql.append("NULL::numeric AS pending_balance ");
        }

        sql.append("FROM patients p ");

        appendEncounterJoin(sql, isAttendedView);

        if (includeBalance || withBalance) {
            appendBalanceJoins(sql);
        }

        boolean hasRange = start != null && end != null;
        boolean isNumeric = numericQuery != null;
        boolean isText = textQueryPattern != null;
        appendFilters(sql, isAttendedView, hasRange, withBalance, isNumeric, isText);

        if (isAttendedView) {
            sql.append("ORDER BY enc.last_encounter_at DESC, p.last_name ASC, p.first_name ASC, p.patient_id ASC ");
        } else {
            sql.append("ORDER BY p.created_at DESC, p.last_name ASC, p.first_name ASC, p.patient_id ASC ");
        }

        sql.append("LIMIT :size OFFSET :offset");

        Query query = entityManager.createNativeQuery(sql.toString());
        setParameters(query, start, end, numericQuery, phonePrefix, textQueryPattern);
        query.setParameter("size", size);
        query.setParameter("offset", offset);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<PatientListRow> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            result.add(new PatientListRow(
                    toUuid(row[0]),
                    (String) row[1],
                    (String) row[2],
                    (String) row[3],
                    (String) row[4],
                    toLocalDate(row[5]),
                    toOffsetDateTime(row[6], zone),
                    toOffsetDateTime(row[7], zone),
                    toBigDecimal(row[8])
            ));
        }
        return result;
    }

    public long countPatientList(
            boolean isAttendedView,
            OffsetDateTime start,
            OffsetDateTime end,
            boolean withBalance,
            String numericQuery,
            String phonePrefix,
            String textQueryPattern
    ) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM patients p ");

        if (isAttendedView) {
            appendEncounterJoin(sql, true);
        }

        if (withBalance) {
            appendBalanceJoins(sql);
        }

        boolean hasRange = start != null && end != null;
        boolean isNumeric = numericQuery != null;
        boolean isText = textQueryPattern != null;
        appendFilters(sql, isAttendedView, hasRange, withBalance, isNumeric, isText);

        Query query = entityManager.createNativeQuery(sql.toString());
        setParameters(query, start, end, numericQuery, phonePrefix, textQueryPattern);

        return ((Number) query.getSingleResult()).longValue();
    }

    private void appendEncounterJoin(StringBuilder sql, boolean isAttendedView) {
        sql.append(isAttendedView ? "JOIN (" : "LEFT JOIN (")
                .append("  SELECT a.patient_id, MAX(e.start_at) AS last_encounter_at ")
                .append("  FROM encounters e ")
                .append("  JOIN appointments a ON a.appointment_id = e.appointment_id ")
                .append("  WHERE e.voided_at IS NULL ")
                .append("  GROUP BY a.patient_id")
                .append(") enc ON enc.patient_id = p.patient_id ");
    }

    private void appendBalanceJoins(StringBuilder sql) {
        sql.append("LEFT JOIN (")
                .append("  SELECT a.patient_id, COALESCE(SUM(pt.price * pt.quantity), 0) AS total_charges ")
                .append("  FROM performed_treatments pt ")
                .append("  JOIN encounters e ON e.encounter_id = pt.encounter_id ")
                .append("  JOIN appointments a ON a.appointment_id = e.appointment_id ")
                .append("  WHERE pt.voided_at IS NULL AND e.voided_at IS NULL ")
                .append("  GROUP BY a.patient_id")
                .append(") ch ON ch.patient_id = p.patient_id ")
                .append("LEFT JOIN (")
                .append("  SELECT py.patient_id, COALESCE(SUM(py.amount), 0) AS total_payments ")
                .append("  FROM payments py ")
                .append("  WHERE py.voided_at IS NULL ")
                .append("  GROUP BY py.patient_id")
                .append(") pm ON pm.patient_id = p.patient_id ");
    }

    private void appendFilters(
            StringBuilder sql,
            boolean isAttendedView,
            boolean hasRange,
            boolean withBalance,
            boolean isNumericQuery,
            boolean isTextQuery
    ) {
        sql.append("WHERE p.active = true AND p.deleted_at IS NULL ");

        if (hasRange) {
            if (isAttendedView) {
                sql.append("AND enc.last_encounter_at >= :start AND enc.last_encounter_at < :end ");
            } else {
                sql.append("AND p.created_at >= :start AND p.created_at < :end ");
            }
        }

        if (withBalance) {
            sql.append("AND (").append(BALANCE_EXPRESSION).append(") > 0 ");
        }

        if (isNumericQuery) {
            sql.append("AND (p.document_number LIKE :numericQuery OR p.phone_e164 LIKE :phonePrefix) ");
        } else if (isTextQuery) {
            sql.append("AND (p.first_name ILIKE :textPattern OR p.last_name ILIKE :textPattern ")
                    .append("OR (p.first_name || ' ' || p.last_name) ILIKE :textPattern) ");
        }
    }

    private void setParameters(
            Query query,
            OffsetDateTime start,
            OffsetDateTime end,
            String numericQuery,
            String phonePrefix,
            String textQueryPattern
    ) {
        if (start != null && end != null) {
            query.setParameter("start", start);
            query.setParameter("end", end);
        }
        if (numericQuery != null) {
            query.setParameter("numericQuery", numericQuery);
            query.setParameter("phonePrefix", phonePrefix);
        } else if (textQueryPattern != null) {
            query.setParameter("textPattern", textQueryPattern);
        }
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

    private static LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate ld) {
            return ld;
        }
        if (value instanceof Date d) {
            return d.toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }

    private static OffsetDateTime toOffsetDateTime(Object value, ZoneId zone) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime odt) {
            return odt;
        }
        if (value instanceof Timestamp ts) {
            return ts.toInstant().atZone(zone).toOffsetDateTime();
        }
        if (value instanceof Instant inst) {
            return inst.atZone(zone).toOffsetDateTime();
        }
        return OffsetDateTime.parse(value.toString());
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd.setScale(2, RoundingMode.HALF_UP);
        }
        return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }
}
