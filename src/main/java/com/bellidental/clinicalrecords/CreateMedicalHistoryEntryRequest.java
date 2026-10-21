package com.bellidental.clinicalrecords;

import com.bellidental.shared.enums.HistoryScope;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateMedicalHistoryEntryRequest(
        @NotNull(message = "El id de condición es obligatorio")
        Long conditionId,

        @NotNull(message = "El alcance (scope) es obligatorio")
        HistoryScope scope,

        @Size(max = 30)
        String relative,

        String description
) {}
