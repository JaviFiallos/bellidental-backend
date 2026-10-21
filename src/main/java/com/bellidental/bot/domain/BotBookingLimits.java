package com.bellidental.bot.domain;

import com.bellidental.appointments.AppointmentSummary;
import com.bellidental.shared.enums.AppointmentSource;
import com.bellidental.shared.exception.BusinessException;

import java.util.Collection;
import java.util.UUID;

/**
 * Regla pura D-9: límites de citas activas del bot.
 * Sin dependencias de Spring.
 */
public final class BotBookingLimits {

    public static final String CODE_LIMIT_REACHED = "BOT_LIMIT_REACHED";

    private BotBookingLimits() {}

    /**
     * Valida que el paciente no tenga ya una cita activa del bot en la misma especialidad
     * y que el teléfono no alcance o supere maxActivePerPhone citas activas del bot.
     * Solo cuentan las citas con source=BOT.
     */
    public static void check(
            Collection<AppointmentSummary> phoneAppointments,
            UUID targetPatientId,
            Long targetSpecialtyId,
            int maxActivePerPhone
    ) {
        long botCount = 0;
        if (phoneAppointments != null) {
            for (AppointmentSummary appointment : phoneAppointments) {
                if (appointment.source() == AppointmentSource.BOT) {
                    botCount++;
                    if (targetPatientId.equals(appointment.patientId()) && targetSpecialtyId.equals(appointment.specialtyId())) {
                        throw new BusinessException(
                                "El paciente ya tiene una cita activa en esta especialidad.",
                                CODE_LIMIT_REACHED
                        );
                    }
                }
            }
        }

        if (botCount >= maxActivePerPhone) {
            throw new BusinessException(
                    "Se ha alcanzado el límite de citas activas para este número de teléfono.",
                    CODE_LIMIT_REACHED
            );
        }
    }
}
