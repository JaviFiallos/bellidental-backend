package com.bellidental.bot.domain;

import com.bellidental.appointments.AppointmentSummary;
import com.bellidental.shared.enums.AppointmentSource;
import com.bellidental.shared.enums.AppointmentStatus;
import com.bellidental.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BotBookingLimitsTest {

    private final UUID patientA = UUID.randomUUID();
    private final UUID patientB = UUID.randomUUID();
    private final UUID professionalId = UUID.randomUUID();
    private final Long specialtyGeneral = 1L;
    private final Long specialtyEndo = 2L;

    @Test
    void check_patientHasBotAppointmentInSpecialty_limitReached() {
        AppointmentSummary existing = createAppointment(patientA, specialtyGeneral, AppointmentSource.BOT);
        List<AppointmentSummary> phoneAppointments = List.of(existing);

        assertThatThrownBy(() -> BotBookingLimits.check(phoneAppointments, patientA, specialtyGeneral, 3))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("BOT_LIMIT_REACHED");
                });
    }

    @Test
    void check_otherSpecialtyBelowPhoneMax_allowed() {
        AppointmentSummary existing = createAppointment(patientA, specialtyGeneral, AppointmentSource.BOT);
        List<AppointmentSummary> phoneAppointments = List.of(existing);

        assertThatCode(() -> BotBookingLimits.check(phoneAppointments, patientA, specialtyEndo, 3))
                .doesNotThrowAnyException();
    }

    @Test
    void check_phoneAtMaxCountingOnlyBotSource_limitReached() {
        AppointmentSummary bot1 = createAppointment(patientA, specialtyGeneral, AppointmentSource.BOT);
        AppointmentSummary bot2 = createAppointment(patientB, specialtyGeneral, AppointmentSource.BOT);
        AppointmentSummary bot3 = createAppointment(patientB, specialtyEndo, AppointmentSource.BOT);
        AppointmentSummary reception = createAppointment(patientA, specialtyEndo, AppointmentSource.RECEPTION);

        // maxActivePerPhone = 3; total bot = 3, reception = 1 (reception no cuenta)
        List<AppointmentSummary> phoneAppointments = List.of(bot1, bot2, bot3, reception);

        assertThatThrownBy(() -> BotBookingLimits.check(phoneAppointments, patientA, specialtyEndo, 3))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("BOT_LIMIT_REACHED");
                });
    }

    private AppointmentSummary createAppointment(UUID patientId, Long specialtyId, AppointmentSource source) {
        return new AppointmentSummary(
                UUID.randomUUID(),
                patientId,
                professionalId,
                specialtyId,
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusMinutes(30),
                AppointmentStatus.PENDING,
                source,
                null,
                null
        );
    }
}
