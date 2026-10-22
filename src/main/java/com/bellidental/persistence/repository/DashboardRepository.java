package com.bellidental.persistence.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Agregados de solo lectura para el panel de estadísticas (D-25). Las consultas son nativas a propósito. */
@Repository
@RequiredArgsConstructor
public class DashboardRepository {

    private static final int MONEY_SCALE = 2;

    private final EntityManager entityManager;

    public long countActivePatients() {
        return longValue(entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM patients WHERE active = true AND deleted_at IS NULL").getSingleResult());
    }

    public long countNewPatients(OffsetDateTime start, OffsetDateTime end) {
        return longValue(rangeQuery(
                "SELECT COUNT(*) FROM patients WHERE created_at >= :start AND created_at < :end AND deleted_at IS NULL",
                start, end).getSingleResult());
    }

    public Map<String, Long> appointmentsByStatus(OffsetDateTime start, OffsetDateTime end) {
        return countsByKey(rangeQuery(
                "SELECT status, COUNT(*) FROM appointments WHERE start_at >= :start AND start_at < :end GROUP BY status",
                start, end));
    }

    /** Solo citas pasadas: una cita futura marcada NO_SHOW no debe superar a las pasadas en la tasa. */
    public long countNoShows(OffsetDateTime start, OffsetDateTime end, OffsetDateTime now) {
        Query query = rangeQuery(
                "SELECT COUNT(*) FROM appointments WHERE status = 'NO_SHOW' AND start_at >= :start AND start_at < :end "
                        + "AND start_at < :now",
                start, end);
        query.setParameter("now", now);
        return longValue(query.getSingleResult());
    }

    public long countPastAppointments(OffsetDateTime start, OffsetDateTime end, OffsetDateTime now) {
        Query query = rangeQuery(
                "SELECT COUNT(*) FROM appointments WHERE start_at >= :start AND start_at < :end AND start_at < :now "
                        + "AND status NOT IN ('CANCELLED', 'RESCHEDULED')",
                start, end);
        query.setParameter("now", now);
        return longValue(query.getSingleResult());
    }

    public long countCompletedEncounters(OffsetDateTime start, OffsetDateTime end) {
        return longValue(rangeQuery(
                "SELECT COUNT(*) FROM encounters WHERE status = 'COMPLETED' AND voided_at IS NULL "
                        + "AND closed_at >= :start AND closed_at < :end",
                start, end).getSingleResult());
    }

    public BigDecimal collectedTotal(OffsetDateTime start, OffsetDateTime end) {
        return money(rangeQuery(
                "SELECT COALESCE(SUM(amount), 0) FROM payments WHERE voided_at IS NULL "
                        + "AND paid_at >= :start AND paid_at < :end",
                start, end).getSingleResult());
    }

    public Map<String, BigDecimal> collectedByMethod(OffsetDateTime start, OffsetDateTime end) {
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        List<Object[]> rows = rangeQuery(
                "SELECT method, COALESCE(SUM(amount), 0) FROM payments WHERE voided_at IS NULL "
                        + "AND paid_at >= :start AND paid_at < :end GROUP BY method",
                start, end).getResultList();
        for (Object[] row : rows) {
            amounts.put((String) row[0], money(row[1]));
        }
        return amounts;
    }

    /** Saldo pendiente de todos los pacientes con saldo positivo (cargos − abonos no anulados). */
    public BigDecimal outstandingBalance() {
        return money(entityManager.createNativeQuery("""
                SELECT COALESCE(SUM(balance), 0) FROM (
                  SELECT patient_id, SUM(amount) AS balance FROM (
                    SELECT a.patient_id, pt.price * pt.quantity AS amount
                      FROM performed_treatments pt
                      JOIN encounters e   ON e.encounter_id = pt.encounter_id
                      JOIN appointments a ON a.appointment_id = e.appointment_id
                     WHERE pt.voided_at IS NULL AND e.voided_at IS NULL
                    UNION ALL
                    SELECT p.patient_id, -p.amount FROM payments p WHERE p.voided_at IS NULL
                  ) movements GROUP BY patient_id HAVING SUM(amount) > 0
                ) balances
                """).getSingleResult());
    }

    /** Filas: code, name, SUM(quantity), SUM(price * quantity). */
    public List<Object[]> topTreatments(OffsetDateTime start, OffsetDateTime end) {
        return rangeQuery(
                "SELECT tc.code, tc.name, SUM(pt.quantity), SUM(pt.price * pt.quantity) "
                        + "FROM performed_treatments pt "
                        + "JOIN encounters e ON e.encounter_id = pt.encounter_id "
                        + "JOIN treatment_catalog tc ON tc.treatment_id = pt.treatment_id "
                        + "WHERE pt.voided_at IS NULL AND e.voided_at IS NULL "
                        + "AND e.start_at >= :start AND e.start_at < :end "
                        + "GROUP BY tc.code, tc.name "
                        + "ORDER BY SUM(pt.quantity) DESC, SUM(pt.price * pt.quantity) DESC "
                        + "LIMIT 3",
                start, end).getResultList();
    }

    /** Filas: professional_id, nombre, COUNT(DISTINCT encounter), SUM(price * quantity). */
    public List<Object[]> encountersAndRevenueByProfessional(OffsetDateTime start, OffsetDateTime end) {
        return rangeQuery(
                "SELECT p.professional_id, p.first_name || ' ' || p.last_name, "
                        + "COUNT(DISTINCT e.encounter_id), COALESCE(SUM(pt.price * pt.quantity), 0) "
                        + "FROM encounters e "
                        + "JOIN professionals p ON p.professional_id = e.professional_id "
                        + "LEFT JOIN performed_treatments pt ON pt.encounter_id = e.encounter_id AND pt.voided_at IS NULL "
                        + "WHERE e.status = 'COMPLETED' AND e.voided_at IS NULL "
                        + "AND e.closed_at >= :start AND e.closed_at < :end "
                        + "GROUP BY p.professional_id, p.first_name, p.last_name",
                start, end).getResultList();
    }

    public Map<String, Long> appointmentsBySource(OffsetDateTime start, OffsetDateTime end) {
        return countsByKey(rangeQuery(
                "SELECT source, COUNT(*) FROM appointments WHERE start_at >= :start AND start_at < :end GROUP BY source",
                start, end));
    }

    public Map<LocalDate, Long> appointmentsByDay(OffsetDateTime start, OffsetDateTime end, String zone) {
        Query query = entityManager.createNativeQuery(
                "SELECT CAST((start_at AT TIME ZONE :zone) AS date), COUNT(*) "
                        + "FROM appointments "
                        + "WHERE start_at >= :start AND start_at < :end "
                        + "AND status NOT IN ('CANCELLED', 'RESCHEDULED') "
                        + "GROUP BY 1");
        query.setParameter("start", start);
        query.setParameter("end", end);
        query.setParameter("zone", zone);
        return countsByDate(query);
    }

    public Map<LocalDate, Long> completedEncountersByDay(OffsetDateTime start, OffsetDateTime end, String zone) {
        Query query = entityManager.createNativeQuery(
                "SELECT CAST((closed_at AT TIME ZONE :zone) AS date), COUNT(*) "
                        + "FROM encounters "
                        + "WHERE status = 'COMPLETED' AND voided_at IS NULL "
                        + "AND closed_at >= :start AND closed_at < :end "
                        + "GROUP BY 1");
        query.setParameter("start", start);
        query.setParameter("end", end);
        query.setParameter("zone", zone);
        return countsByDate(query);
    }

    public Map<LocalDate, Long> newPatientsByDay(OffsetDateTime start, OffsetDateTime end, String zone) {
        Query query = entityManager.createNativeQuery(
                "SELECT CAST((created_at AT TIME ZONE :zone) AS date), COUNT(*) "
                        + "FROM patients "
                        + "WHERE created_at >= :start AND created_at < :end "
                        + "AND deleted_at IS NULL "
                        + "GROUP BY 1");
        query.setParameter("start", start);
        query.setParameter("end", end);
        query.setParameter("zone", zone);
        return countsByDate(query);
    }

    public Map<LocalDate, BigDecimal> collectedByDay(OffsetDateTime start, OffsetDateTime end, String zone) {
        Query query = entityManager.createNativeQuery(
                "SELECT CAST((paid_at AT TIME ZONE :zone) AS date), COALESCE(SUM(amount), 0) "
                        + "FROM payments "
                        + "WHERE voided_at IS NULL "
                        + "AND paid_at >= :start AND paid_at < :end "
                        + "GROUP BY 1");
        query.setParameter("start", start);
        query.setParameter("end", end);
        query.setParameter("zone", zone);
        Map<LocalDate, BigDecimal> amounts = new LinkedHashMap<>();
        List<Object[]> rows = query.getResultList();
        for (Object[] row : rows) {
            amounts.put(toLocalDate(row[0]), money(row[1]));
        }
        return amounts;
    }

    public List<Object[]> topDiagnoses(OffsetDateTime start, OffsetDateTime end) {
        return rangeQuery("""
                SELECT ic.code, ic.description, COUNT(*) AS occurrences, COUNT(DISTINCT a.patient_id) AS patients
                  FROM encounter_diagnoses ed
                  JOIN encounters e    ON e.encounter_id = ed.encounter_id
                  JOIN appointments a  ON a.appointment_id = e.appointment_id
                  JOIN icd10_codes ic  ON ic.icd10_id = ed.icd10_id
                 WHERE ed.voided_at IS NULL AND e.voided_at IS NULL
                   AND e.start_at >= :start AND e.start_at < :end
                 GROUP BY ic.code, ic.description
                 ORDER BY COUNT(*) DESC, ic.code ASC
                 LIMIT 3
                """, start, end).getResultList();
    }

    private Query rangeQuery(String sql, OffsetDateTime start, OffsetDateTime end) {
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("start", start);
        query.setParameter("end", end);
        return query;
    }

    private Map<String, Long> countsByKey(Query query) {
        Map<String, Long> counts = new LinkedHashMap<>();
        List<Object[]> rows = query.getResultList();
        for (Object[] row : rows) {
            counts.put((String) row[0], longValue(row[1]));
        }
        return counts;
    }

    private Map<LocalDate, Long> countsByDate(Query query) {
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        List<Object[]> rows = query.getResultList();
        for (Object[] row : rows) {
            counts.put(toLocalDate(row[0]), longValue(row[1]));
        }
        return counts;
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof Date d) {
            return d.toLocalDate();
        }
        if (value instanceof LocalDate ld) {
            return ld;
        }
        return LocalDate.parse(value.toString());
    }

    private static long longValue(Object value) {
        return ((Number) value).longValue();
    }

    private static BigDecimal money(Object value) {
        return new BigDecimal(value.toString()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
