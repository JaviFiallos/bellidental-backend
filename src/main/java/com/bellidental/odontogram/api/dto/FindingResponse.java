package com.bellidental.odontogram.api.dto;

import com.bellidental.shared.enums.FindingStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FindingResponse(
        UUID id,
        Short toothCode,
        Short endToothCode,
        String surfaceCode,
        String surfaceLabel,
        Long conditionId,
        FindingStatus status,
        Short grade,
        String notes,
        OffsetDateTime createdAt,
        SymbolResponse symbol
) {
}
