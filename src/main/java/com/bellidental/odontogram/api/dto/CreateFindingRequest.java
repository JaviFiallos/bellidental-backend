package com.bellidental.odontogram.api.dto;

import com.bellidental.shared.enums.FindingStatus;
import jakarta.validation.constraints.NotNull;

public record CreateFindingRequest(
        @NotNull Short toothCode,
        Short endToothCode,
        String surfaceCode,
        @NotNull Long conditionId,
        @NotNull FindingStatus status,
        Short grade,
        String notes
) {
}
