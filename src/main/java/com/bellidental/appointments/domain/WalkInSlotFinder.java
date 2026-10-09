package com.bellidental.appointments.domain;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class WalkInSlotFinder {

    public static Optional<OffsetDateTime> findFirst(OffsetDateTime now, int durationMinutes,
                                                     List<TimeRange> busy, OffsetDateTime endOfDay) {
        Duration duration = Duration.ofMinutes(durationMinutes);
        OffsetDateTime candidateStart = now.truncatedTo(ChronoUnit.MINUTES);

        Optional<TimeRange> blocking = findBlocking(candidateStart, duration, busy);
        while (blocking.isPresent()) {
            candidateStart = blocking.get().endAt();
            blocking = findBlocking(candidateStart, duration, busy);
        }

        if (candidateStart.plus(duration).isAfter(endOfDay)) {
            return Optional.empty();
        }
        return Optional.of(candidateStart);
    }

    private static Optional<TimeRange> findBlocking(OffsetDateTime candidateStart, Duration duration, List<TimeRange> busy) {
        TimeRange candidate = new TimeRange(candidateStart, candidateStart.plus(duration));
        return busy.stream()
                .filter(candidate::overlaps)
                .max(Comparator.comparing(TimeRange::endAt));
    }
}
