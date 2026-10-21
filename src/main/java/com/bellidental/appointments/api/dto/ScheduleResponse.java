package com.bellidental.appointments.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record ScheduleResponse(
    Long id,
    Short weekday,
    LocalDate specificDate,
    LocalTime startTime,
    LocalTime endTime,
    boolean available
) {
}
