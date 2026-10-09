package com.bellidental.appointments;

import java.time.OffsetDateTime;

public record AvailableSlot(OffsetDateTime startAt, OffsetDateTime endAt) {
}
