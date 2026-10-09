package com.bellidental.appointments.domain;

import java.time.LocalTime;

public record Shift(LocalTime startTime, LocalTime endTime) {
}
