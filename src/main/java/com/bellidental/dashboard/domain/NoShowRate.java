package com.bellidental.dashboard.domain;

import com.bellidental.shared.exception.InvalidInputException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Tasa de inasistencia: NO_SHOW / citas pasadas no canceladas ni reprogramadas, en porcentaje (0–100). */
public final class NoShowRate {

    private static final int SCALE = 1;

    private NoShowRate() {}

    public static BigDecimal of(long noShows, long pastAppointments) {
        if (noShows < 0 || pastAppointments < 0 || noShows > pastAppointments) {
            throw new InvalidInputException("Las inasistencias no pueden superar a las citas pasadas");
        }
        if (pastAppointments == 0) {
            return BigDecimal.ZERO.setScale(SCALE);
        }
        return BigDecimal.valueOf(noShows * 100)
                .divide(BigDecimal.valueOf(pastAppointments), SCALE, RoundingMode.HALF_UP);
    }
}
