package com.bellidental.clinicalrecords;

import com.bellidental.shared.enums.HistoryScope;
import com.bellidental.persistence.entity.MedicalHistoryEntry;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MedicalHistoryEntryResponse(
        UUID id,
        Long conditionId,
        String conditionName,
        HistoryScope scope,
        String relative,
        String description,
        boolean active,
        OffsetDateTime recordedAt,
        OffsetDateTime voidedAt
) {
    public static MedicalHistoryEntryResponse from(MedicalHistoryEntry entry) {
        return new MedicalHistoryEntryResponse(
                entry.getId(),
                entry.getCondition().getId(),
                entry.getCondition().getName(),
                entry.getScope(),
                entry.getRelative(),
                entry.getDescription(),
                entry.isActive(),
                entry.getRecordedAt(),
                entry.getVoidedAt()
        );
    }
}
