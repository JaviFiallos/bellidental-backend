package com.bellidental.appointments.domain;

import com.bellidental.appointments.AvailableSlot;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SlotCalculatorTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guayaquil");
    private static final LocalDate DAY = LocalDate.of(2026, 10, 20);

    private OffsetDateTime at(int hour, int minute) {
        return DAY.atTime(hour, minute).atZone(ZONE).toOffsetDateTime();
    }

    @Test
    void calculate_busyAppointmentAndEarlyNow_skipsOverlappingSlots() {
        List<Shift> shifts = List.of(new Shift(LocalTime.of(8, 0), LocalTime.of(10, 0)));
        List<TimeRange> busy = List.of(new TimeRange(at(8, 30), at(9, 0)));

        List<AvailableSlot> slots = SlotCalculator.calculate(DAY, shifts, 30, busy, at(7, 0), ZONE);

        assertThat(slots).extracting(AvailableSlot::startAt)
                .containsExactly(at(8, 0), at(9, 0), at(9, 30));
    }

    @Test
    void calculate_partialLastSlot_isDiscarded() {
        List<Shift> shifts = List.of(new Shift(LocalTime.of(8, 0), LocalTime.of(9, 45)));

        List<AvailableSlot> slots = SlotCalculator.calculate(DAY, shifts, 30, List.of(), at(7, 0), ZONE);

        assertThat(slots).extracting(AvailableSlot::startAt)
                .containsExactly(at(8, 0), at(8, 30), at(9, 0));
    }
}
