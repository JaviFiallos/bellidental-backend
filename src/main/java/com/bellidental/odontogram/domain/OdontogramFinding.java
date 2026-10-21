package com.bellidental.odontogram.domain;

import com.bellidental.shared.enums.Arch;
import com.bellidental.shared.enums.ConditionScope;
import com.bellidental.shared.enums.Dentition;
import com.bellidental.shared.enums.FindingStatus;
import com.bellidental.shared.enums.ToothType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OdontogramFinding(
        UUID id,
        Short toothCode,
        Short endToothCode,
        String surfaceCode,
        Long conditionId,
        String conditionCode,
        ConditionScope scope,
        String cpoComponent,
        String ceoComponent,
        Dentition dentition,
        Arch arch,
        ToothType toothType,
        FindingStatus status,
        Short grade,
        String notes,
        OffsetDateTime createdAt,
        String surfaceName
) {
}
