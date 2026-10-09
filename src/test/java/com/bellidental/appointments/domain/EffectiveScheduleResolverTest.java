package com.bellidental.appointments.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EffectiveScheduleResolverTest {

    @Test
    void resolve_weeklySchedule_returnsShiftsOrdered() {
        LocalDate monday = LocalDate.of(2023, 1, 2); // Monday
        List<ScheduleRule> schedules = List.of(
                new ScheduleRule((short) 1, null, LocalTime.of(8, 0), LocalTime.of(12, 0), true),
                new ScheduleRule((short) 1, null, LocalTime.of(14, 0), LocalTime.of(18, 0), true)
        );

        List<Shift> shifts = EffectiveScheduleResolver.resolve(monday, schedules);

        assertThat(shifts).hasSize(2);
        assertThat(shifts.get(0).startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(shifts.get(1).startTime()).isEqualTo(LocalTime.of(14, 0));
    }

    @Test
    void resolve_specificDateOverridesWeekly_returnsSpecificShift() {
        LocalDate monday = LocalDate.of(2023, 1, 2);
        List<ScheduleRule> schedules = List.of(
                new ScheduleRule((short) 1, null, LocalTime.of(8, 0), LocalTime.of(12, 0), true),
                new ScheduleRule(null, monday, LocalTime.of(10, 0), LocalTime.of(14, 0), true)
        );

        List<Shift> shifts = EffectiveScheduleResolver.resolve(monday, schedules);

        assertThat(shifts).hasSize(1);
        assertThat(shifts.get(0).startTime()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    void resolve_specificDateUnavailable_returnsEmpty() {
        LocalDate monday = LocalDate.of(2023, 1, 2);
        List<ScheduleRule> schedules = List.of(
                new ScheduleRule((short) 1, null, LocalTime.of(8, 0), LocalTime.of(12, 0), true),
                new ScheduleRule(null, monday, null, null, false)
        );

        List<Shift> shifts = EffectiveScheduleResolver.resolve(monday, schedules);

        assertThat(shifts).isEmpty();
    }

    @Test
    void resolve_noSchedules_returnsEmpty() {
        LocalDate monday = LocalDate.of(2023, 1, 2);
        List<Shift> shifts = EffectiveScheduleResolver.resolve(monday, List.of());

        assertThat(shifts).isEmpty();
    }
}
