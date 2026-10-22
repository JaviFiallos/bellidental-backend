package com.bellidental.audit.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogEntryResponse(
        Long revision,
        Instant at,
        UUID userId,
        String username,
        String entity,
        String entityId,
        String type,
        UUID patientId,
        String patientName
) {}
