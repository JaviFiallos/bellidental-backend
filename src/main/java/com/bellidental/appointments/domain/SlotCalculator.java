package com.bellidental.appointments.domain;

import com.bellidental.appointments.AvailableSlot;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class SlotCalculator {

    public static List<AvailableSlot> calculate(LocalDate day, List<Shift> shifts, int durationMinutes,
                                                List<TimeRange> busy, OffsetDateTime now, ZoneId zone) {
        List<AvailableSlot> slots = new ArrayList<>();
        if (durationMinutes <= 0) {
            return slots;
        }
        Duration duration = Duration.ofMinutes(durationMinutes);

        for (Shift shift : shifts) {
            OffsetDateTime shiftEnd = day.atTime(shift.endTime()).atZone(zone).toOffsetDateTime();
            OffsetDateTime candidateStart = day.atTime(shift.startTime()).atZone(zone).toOffsetDateTime();

            while (!candidateStart.plus(duration).isAfter(shiftEnd)) {
                TimeRange candidate = new TimeRange(candidateStart, candidateStart.plus(duration));
                if (candidate.startAt().isAfter(now) && busy.stream().noneMatch(candidate::overlaps)) {
                    slots.add(new AvailableSlot(candidate.startAt(), candidate.endAt()));
                }
                candidateStart = candidate.endAt();
            }
        }
        return slots;
    }
}
