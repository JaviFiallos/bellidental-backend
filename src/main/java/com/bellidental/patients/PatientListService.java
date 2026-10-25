package com.bellidental.patients;

import com.bellidental.persistence.repository.PatientListQueryRepository;
import com.bellidental.persistence.repository.PatientListQueryRepository.PatientListRow;
import com.bellidental.shared.enums.DocumentType;
import com.bellidental.shared.exception.ForbiddenException;
import com.bellidental.shared.exception.InvalidInputException;
import com.bellidental.shared.time.ClinicClock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Servicio de consulta informativa de pacientes con agregados y control de permisos sobre saldos.
 */
@Service
@RequiredArgsConstructor
public class PatientListService {

    private static final int MAX_RANGE_DAYS = 366;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final PatientListQueryRepository patientListQueryRepository;
    private final ClinicClock clinicClock;

    @Transactional(readOnly = true)
    public Page<PatientListItem> listPatients(
            String view,
            LocalDate from,
            LocalDate to,
            boolean withBalance,
            String query,
            int page,
            int size
    ) {
        PatientListView parsedView = parseView(view);
        validateDateRange(from, to);

        boolean hasBillingRead = hasBillingReadAuthority();
        if (withBalance && !hasBillingRead) {
            throw new ForbiddenException("No tienes permiso para ver saldos.");
        }

        if (size > MAX_PAGE_SIZE) {
            size = MAX_PAGE_SIZE;
        } else if (size < 1) {
            size = DEFAULT_PAGE_SIZE;
        }

        if (page < 0) {
            page = 0;
        }

        OffsetDateTime start = null;
        OffsetDateTime end = null;
        if (from != null && to != null) {
            start = from.atStartOfDay(clinicClock.zone()).toOffsetDateTime();
            end = to.plusDays(1).atStartOfDay(clinicClock.zone()).toOffsetDateTime();
        }

        String numericQuery = null;
        String phonePrefix = null;
        String textQueryPattern = null;
        if (query != null && !query.isBlank()) {
            String trimmed = query.trim();
            if (trimmed.matches("\\d+")) {
                numericQuery = trimmed + "%";
                phonePrefix = PatientService.toPhonePrefix(trimmed) + "%";
            } else {
                textQueryPattern = "%" + trimmed + "%";
            }
        }

        boolean isAttendedView = parsedView == PatientListView.ATTENDED;
        long total = patientListQueryRepository.countPatientList(
                isAttendedView, start, end, withBalance, numericQuery, phonePrefix, textQueryPattern
        );

        List<PatientListItem> items;
        if (total == 0) {
            items = List.of();
        } else {
            int offset = page * size;
            List<PatientListRow> rows = patientListQueryRepository.findPatientList(
                    isAttendedView,
                    start,
                    end,
                    withBalance,
                    hasBillingRead,
                    numericQuery,
                    phonePrefix,
                    textQueryPattern,
                    offset,
                    size,
                    clinicClock.zone()
            );
            LocalDate today = clinicClock.today();
            items = rows.stream()
                    .map(row -> toItem(row, today))
                    .toList();
        }

        return new PageImpl<>(items, PageRequest.of(page, size), total);
    }

    private PatientListView parseView(String view) {
        if (view == null || view.isBlank()) {
            throw new InvalidInputException("Indica la vista: ATTENDED o CREATED.");
        }
        try {
            return PatientListView.valueOf(view.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Indica la vista: ATTENDED o CREATED.");
        }
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if ((from == null) != (to == null)) {
            throw new InvalidInputException("Indica las dos fechas del período, o ninguna.");
        }
        if (from != null && to != null) {
            if (from.isAfter(to)) {
                throw new InvalidInputException("La fecha inicial no puede ser posterior a la final.");
            }
            if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
                throw new InvalidInputException("El período no puede superar 366 días.");
            }
        }
    }

    private boolean hasBillingReadAuthority() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> "BILLING_READ".equals(a.getAuthority()));
    }

    private PatientListItem toItem(PatientListRow row, LocalDate today) {
        Integer age = null;
        if (row.birthDate() != null) {
            age = Period.between(row.birthDate(), today).getYears();
        }

        return new PatientListItem(
                row.id(),
                row.firstName(),
                row.lastName(),
                row.firstName() + " " + row.lastName(),
                DocumentType.valueOf(row.documentType()),
                row.documentNumber(),
                age,
                row.lastEncounterAt(),
                row.createdAt(),
                row.pendingBalance()
        );
    }
}
