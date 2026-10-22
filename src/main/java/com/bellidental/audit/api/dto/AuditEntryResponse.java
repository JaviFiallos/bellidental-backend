package com.bellidental.audit.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuditEntryResponse(
        Long revision,
        Instant at,
        UUID userId,
        String username,
        String type,
        List<FieldChangeResponse> changes,
        String entity,
        String entityId
) {}
