package com.bellidental.appointments.domain;

import java.time.LocalDate;
import java.time.LocalTime;

public record ScheduleRule(
        Short weekday,
        LocalDate specificDate,
        LocalTime startTime,
        LocalTime endTime,
        boolean available
) {
}
