package com.bellidental.bot.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BotProfessionalScheduleResponse(
        UUID professionalId,
        String professionalName,
        List<ScheduleDay> days
) {

    public record ScheduleDay(LocalDate date, List<ScheduleShift> shifts) {}

    public record ScheduleShift(String startTime, String endTime) {}
}
