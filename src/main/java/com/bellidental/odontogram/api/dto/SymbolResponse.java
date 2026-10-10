package com.bellidental.odontogram.api.dto;

import com.bellidental.shared.enums.FindingStatus;

public record SymbolResponse(
        Long id,
        FindingStatus status,
        String shape,
        String color,
        String secondaryColor,
        Boolean filled,
        String lineStyle
) {
}
