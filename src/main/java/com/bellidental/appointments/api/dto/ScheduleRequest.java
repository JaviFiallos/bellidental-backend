package com.bellidental.appointments.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record ScheduleRequest(
    @Min(1) @Max(7) Short weekday,
    LocalDate specificDate,
    LocalTime startTime,
    LocalTime endTime,
    @NotNull Boolean available
) {
}
