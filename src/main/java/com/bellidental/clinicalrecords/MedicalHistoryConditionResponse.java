package com.bellidental.clinicalrecords;

import com.bellidental.persistence.entity.MedicalHistoryCondition;

public record MedicalHistoryConditionResponse(
        Long id,
        String name,
        short displayOrder
) {
    public static MedicalHistoryConditionResponse from(MedicalHistoryCondition condition) {
        return new MedicalHistoryConditionResponse(
                condition.getId(),
                condition.getName(),
                condition.getDisplayOrder()
        );
    }
}
