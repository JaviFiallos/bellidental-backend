package com.bellidental.audit.application;

import com.bellidental.audit.api.dto.AuditLogEntryResponse;
import com.bellidental.persistence.repository.AuditQueryRepository;
import com.bellidental.shared.exception.InvalidInputException;
import com.bellidental.shared.time.ClinicClock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final int MAX_RANGE_DAYS = 92;

    private final AuditQueryRepository auditQueryRepository;
    private final ClinicClock clinicClock;

    @Transactional(readOnly = true)
    public Page<AuditLogEntryResponse> listAudit(
            LocalDate requestedFrom,
            LocalDate requestedTo,
            String entity,
            UUID userId,
            UUID patientId,
            int page,
            int size
    ) {
        if ((requestedFrom == null) != (requestedTo == null)) {
            throw new InvalidInputException("Indique ambas fechas (from y to) o ninguna.");
        }

        LocalDate to = requestedTo != null ? requestedTo : clinicClock.today();
        LocalDate from = requestedFrom != null ? requestedFrom : to.minusDays(6);

        if (from.isAfter(to)) {
            throw new InvalidInputException("La fecha inicial no puede ser posterior a la final.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new InvalidInputException("El rango de fechas no puede superar 92 días.");
        }

        if (entity != null && !AuditService.AUDITED_ENTITIES.containsKey(entity)) {
            throw new InvalidInputException("Entidad desconocida: " + entity);
        }

        if (page < 0) {
            throw new InvalidInputException("El número de página no puede ser negativo.");
        }
        if (size < 1 || size > 100) {
            throw new InvalidInputException("El tamaño de página debe estar entre 1 y 100.");
        }

        OffsetDateTime start = from.atStartOfDay(clinicClock.zone()).toOffsetDateTime();
        OffsetDateTime end = to.plusDays(1).atStartOfDay(clinicClock.zone()).toOffsetDateTime();

        long total = auditQueryRepository.countAuditLog(start, end, entity, userId, patientId);
        List<AuditLogEntryResponse> content = total == 0
                ? List.of()
                : auditQueryRepository.findAuditLog(start, end, entity, userId, patientId, page * size, size).stream()
                        .map(row -> new AuditLogEntryResponse(row.revision(), row.at(), row.userId(), row.username(),
                                row.entity(), row.entityId(), row.type(), row.patientId(), row.patientName()))
                        .toList();

        return new PageImpl<>(content, PageRequest.of(page, size), total);
    }
}
