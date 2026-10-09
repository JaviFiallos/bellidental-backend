package com.bellidental.appointments.domain;

import java.time.OffsetDateTime;

public record TimeRange(OffsetDateTime startAt, OffsetDateTime endAt) {

    /** Extremos que solo se tocan no cruzan. */
    public boolean overlaps(TimeRange other) {
        return startAt.isBefore(other.endAt) && other.startAt.isBefore(endAt);
    }
}
