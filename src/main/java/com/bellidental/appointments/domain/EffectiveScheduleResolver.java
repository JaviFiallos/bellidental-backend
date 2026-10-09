package com.bellidental.appointments.domain;



import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public class EffectiveScheduleResolver {

    public static List<Shift> resolve(LocalDate day, List<ScheduleRule> activeSchedules) {
        List<ScheduleRule> forSpecificDate = activeSchedules.stream()
                .filter(s -> day.equals(s.specificDate()))
                .toList();

        List<ScheduleRule> chosenSchedules;
        if (!forSpecificDate.isEmpty()) {
            chosenSchedules = forSpecificDate;
        } else {
            chosenSchedules = activeSchedules.stream()
                    .filter(s -> s.weekday() != null && s.weekday().intValue() == day.getDayOfWeek().getValue())
                    .toList();
        }

        if (chosenSchedules.stream().anyMatch(s -> !s.available())) {
            return List.of();
        }

        return chosenSchedules.stream()
                .sorted(Comparator.comparing(ScheduleRule::startTime))
                .map(s -> new Shift(s.startTime(), s.endTime()))
                .toList();
    }
}
