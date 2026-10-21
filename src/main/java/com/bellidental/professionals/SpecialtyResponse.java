package com.bellidental.professionals;

import com.bellidental.persistence.entity.Specialty;

public record SpecialtyResponse(
        Long id,
        String name,
        int defaultDurationMinutes
) {
    public static SpecialtyResponse from(Specialty s) {
        return new SpecialtyResponse(
                s.getId(),
                s.getName(),
                s.getDefaultDurationMinutes()
        );
    }
}
