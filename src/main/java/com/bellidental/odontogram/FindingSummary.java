package com.bellidental.odontogram;

import com.bellidental.shared.enums.FindingStatus;

import java.util.UUID;

public record FindingSummary(
        UUID id,
        UUID encounterId,
        Short toothCode,
        Short endToothCode,
        String surfaceCode,
        Long conditionId,
        FindingStatus status,
        boolean voided
) {}
