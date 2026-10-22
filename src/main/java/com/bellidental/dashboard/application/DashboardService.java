package com.bellidental.dashboard.application;

import com.bellidental.dashboard.api.dto.AppointmentStats;
import com.bellidental.dashboard.api.dto.BillingStats;
import com.bellidental.dashboard.api.dto.ChannelStat;
import com.bellidental.dashboard.api.dto.DailyStat;
import com.bellidental.dashboard.api.dto.DashboardSummaryResponse;
import com.bellidental.dashboard.api.dto.PatientStats;
import com.bellidental.dashboard.api.dto.ProfessionalStats;
import com.bellidental.dashboard.api.dto.TopDiagnosisResponse;
import com.bellidental.dashboard.api.dto.TopTreatmentResponse;
import com.bellidental.dashboard.domain.NoShowRate;
import com.bellidental.persistence.repository.DashboardRepository;
import com.bellidental.shared.enums.AppointmentSource;
import com.bellidental.shared.enums.AppointmentStatus;
import com.bellidental.shared.enums.PaymentMethod;
import com.bellidental.shared.exception.InvalidInputException;
import com.bellidental.shared.time.ClinicClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int MAX_RANGE_DAYS = 366;
    private static final int MONEY_SCALE = 2;

    private final DashboardRepository dashboardRepository;
    private final ClinicClock clinicClock;

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summarize(LocalDate requestedFrom, LocalDate requestedTo) {
        LocalDate today = clinicClock.today();
        LocalDate from = requestedFrom != null ? requestedFrom : today.withDayOfMonth(1);
        LocalDate to = requestedTo != null ? requestedTo : today;
        validateRange(requestedFrom, requestedTo, from, to);

        OffsetDateTime start = from.atStartOfDay(clinicClock.zone()).toOffsetDateTime();
        OffsetDateTime end = to.plusDays(1).atStartOfDay(clinicClock.zone()).toOffsetDateTime();
        OffsetDateTime now = clinicClock.now();

        String zone = clinicClock.zone().getId();
        Map<LocalDate, Long> appointmentsByDay = dashboardRepository.appointmentsByDay(start, end, zone);
        Map<LocalDate, Long> completedEncountersByDay = dashboardRepository.completedEncountersByDay(start, end, zone);
        Map<LocalDate, Long> newPatientsByDay = dashboardRepository.newPatientsByDay(start, end, zone);
        Map<LocalDate, BigDecimal> collectedByDay = dashboardRepository.collectedByDay(start, end, zone);

        List<DailyStat> byDay = new ArrayList<>();
        BigDecimal zeroMoney = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            byDay.add(new DailyStat(
                    date,
                    appointmentsByDay.getOrDefault(date, 0L),
                    completedEncountersByDay.getOrDefault(date, 0L),
                    newPatientsByDay.getOrDefault(date, 0L),
                    collectedByDay.getOrDefault(date, zeroMoney)
            ));
        }

        List<TopDiagnosisResponse> topDiagnoses = dashboardRepository.topDiagnoses(start, end).stream()
                .map(this::toTopDiagnosis)
                .toList();

        return new DashboardSummaryResponse(
                from,
                to,
                new PatientStats(
                        dashboardRepository.countActivePatients(),
                        dashboardRepository.countNewPatients(start, end)
                ),
                appointmentStats(start, end, now),
                billingStats(start, end),
                dashboardRepository.topTreatments(start, end).stream().map(this::toTopTreatment).toList(),
                dashboardRepository.encountersAndRevenueByProfessional(start, end).stream()
                        .map(this::toProfessionalStats)
                        .toList(),
                channelStats(start, end),
                topDiagnoses,
                byDay
        );
    }

    private void validateRange(LocalDate requestedFrom, LocalDate requestedTo, LocalDate from, LocalDate to) {
        if ((requestedFrom == null) != (requestedTo == null)) {
            throw new InvalidInputException("Indique ambas fechas (from y to) o ninguna");
        }
        if (from.isAfter(to)) {
            throw new InvalidInputException("La fecha inicial no puede ser posterior a la final");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new InvalidInputException("El rango no puede superar 366 días");
        }
    }

    private AppointmentStats appointmentStats(OffsetDateTime start, OffsetDateTime end, OffsetDateTime now) {
        Map<String, Long> byStatus = fillByName(AppointmentStatus.class,
                dashboardRepository.appointmentsByStatus(start, end), 0L);
        long noShows = dashboardRepository.countNoShows(start, end, now);
        long pastAppointments = dashboardRepository.countPastAppointments(start, end, now);
        return new AppointmentStats(
                byStatus.values().stream().mapToLong(Long::longValue).sum(),
                byStatus,
                NoShowRate.of(noShows, pastAppointments),
                dashboardRepository.countCompletedEncounters(start, end)
        );
    }

    private BillingStats billingStats(OffsetDateTime start, OffsetDateTime end) {
        BigDecimal zero = BigDecimal.ZERO.setScale(MONEY_SCALE);
        return new BillingStats(
                dashboardRepository.collectedTotal(start, end),
                fillByName(PaymentMethod.class, dashboardRepository.collectedByMethod(start, end), zero),
                dashboardRepository.outstandingBalance()
        );
    }

    private List<ChannelStat> channelStats(OffsetDateTime start, OffsetDateTime end) {
        Map<String, Long> bySource = dashboardRepository.appointmentsBySource(start, end);
        return Arrays.stream(AppointmentSource.values())
                .map(source -> new ChannelStat(source.name(), bySource.getOrDefault(source.name(), 0L)))
                .toList();
    }

    private TopTreatmentResponse toTopTreatment(Object[] row) {
        return new TopTreatmentResponse(
                (String) row[0],
                (String) row[1],
                ((Number) row[2]).longValue(),
                money(row[3])
        );
    }

    private TopDiagnosisResponse toTopDiagnosis(Object[] row) {
        return new TopDiagnosisResponse(
                (String) row[0],
                (String) row[1],
                ((Number) row[2]).longValue(),
                ((Number) row[3]).longValue()
        );
    }

    private ProfessionalStats toProfessionalStats(Object[] row) {
        return new ProfessionalStats(
                UUID.fromString(row[0].toString()),
                (String) row[1],
                ((Number) row[2]).longValue(),
                money(row[3])
        );
    }

    /** Mapa con todas las constantes del enum, en orden de declaración, aunque no tengan datos. */
    private static <E extends Enum<E>, V> Map<String, V> fillByName(Class<E> type, Map<String, V> values, V defaultValue) {
        Map<String, V> filled = new LinkedHashMap<>();
        for (E constant : type.getEnumConstants()) {
            filled.put(constant.name(), values.getOrDefault(constant.name(), defaultValue));
        }
        return filled;
    }

    private static BigDecimal money(Object value) {
        return new BigDecimal(value.toString()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
