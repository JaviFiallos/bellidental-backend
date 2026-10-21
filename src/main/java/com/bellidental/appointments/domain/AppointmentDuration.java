package com.bellidental.appointments.domain;

import com.bellidental.shared.exception.InvalidInputException;

import java.time.Duration;
import java.time.OffsetDateTime;

public final class AppointmentDuration {

    public static final int MIN_MINUTES = 15;
    public static final int MAX_MINUTES = 240;
    public static final int STEP_MINUTES = 15;
    public static final String INVALID_DURATION_MESSAGE = "La duración debe ser múltiplo de 15 minutos, entre 15 y 240.";

    private AppointmentDuration() {
    }

    public static void validate(int minutes) {
        if (minutes < MIN_MINUTES || minutes > MAX_MINUTES || minutes % STEP_MINUTES != 0) {
            throw new InvalidInputException(INVALID_DURATION_MESSAGE);
        }
    }

    public static void validate(OffsetDateTime startAt, OffsetDateTime endAt) {
        Duration duration = Duration.between(startAt, endAt);
        if (duration.toSeconds() % 60 != 0) {
            throw new InvalidInputException(INVALID_DURATION_MESSAGE);
        }
        validate((int) duration.toMinutes());
    }
}
