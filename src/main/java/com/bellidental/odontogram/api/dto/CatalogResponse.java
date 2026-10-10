package com.bellidental.odontogram.api.dto;

import com.bellidental.shared.enums.Arch;
import com.bellidental.shared.enums.ConditionCategory;
import com.bellidental.shared.enums.ConditionScope;
import com.bellidental.shared.enums.Dentition;
import com.bellidental.shared.enums.ToothType;

import java.util.List;

public record CatalogResponse(
        List<ToothDto> teeth,
        List<SurfaceDto> surfaces,
        List<ConditionDto> conditions
) {
    public record ToothDto(Short code, Dentition dentition, Arch arch, Short quadrant, Short position, ToothType type) {}
    public record SurfaceDto(String code, String name) {}
    public record ConditionDto(
            Long id, String code, String name, ConditionCategory category, ConditionScope scope, 
            Short gradeMin, Short gradeMax, List<SymbolResponse> symbols) {}
}
