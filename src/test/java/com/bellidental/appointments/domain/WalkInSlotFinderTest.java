package com.bellidental.appointments.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WalkInSlotFinderTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guayaquil");
    private static final LocalDate DAY = LocalDate.of(2026, 10, 20);

    private OffsetDateTime at(int hour, int minute) {
        return DAY.atTime(hour, minute).atZone(ZONE).toOffsetDateTime();
    }

    @Test
    void findFirst_consecutiveAppointments_returnsEndOfLastOne() {
        List<TimeRange> busy = List.of(
                new TimeRange(at(10, 0), at(10, 30)),
                new TimeRange(at(10, 30), at(11, 0)));

        var slot = WalkInSlotFinder.findFirst(at(10, 10), 30, busy, at(23, 59));

        assertThat(slot).contains(at(11, 0));
    }

    @Test
    void findFirst_noRoomBeforeEndOfDay_returnsEmpty() {
        var slot = WalkInSlotFinder.findFirst(at(23, 50), 30, List.of(), at(23, 59));

        assertThat(slot).isEmpty();
    }
}
