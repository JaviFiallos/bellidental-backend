package com.bellidental.professionals;

public record SpecialtySummary(
    Long id,
    String name,
    Integer defaultDurationMinutes,
    boolean active
) {
}
