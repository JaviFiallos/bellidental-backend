package com.bellidental.appointments.domain;

import com.bellidental.shared.enums.AppointmentStatus;
import com.bellidental.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppointmentTransitionsTest {

    @Test
    void confirm_fromPending_returnsConfirmed() {
        assertThat(AppointmentTransitions.confirm(AppointmentStatus.PENDING)).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test
    void confirm_fromConfirmed_throwsBusinessException() {
        assertThatThrownBy(() -> AppointmentTransitions.confirm(AppointmentStatus.CONFIRMED))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void cancel_fromValidStates_returnsCancelled() {
        assertThat(AppointmentTransitions.cancel(AppointmentStatus.PENDING)).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(AppointmentTransitions.cancel(AppointmentStatus.CONFIRMED)).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void cancel_fromCancelled_throwsBusinessException() {
        assertThatThrownBy(() -> AppointmentTransitions.cancel(AppointmentStatus.CANCELLED))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void checkIn_fromValidStates_returnsSameState() {
        assertThat(AppointmentTransitions.checkIn(AppointmentStatus.PENDING, false)).isEqualTo(AppointmentStatus.PENDING);
        assertThat(AppointmentTransitions.checkIn(AppointmentStatus.CONFIRMED, false)).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test
    void checkIn_whenAlreadyCheckedIn_throwsBusinessException() {
        assertThatThrownBy(() -> AppointmentTransitions.checkIn(AppointmentStatus.PENDING, true))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void noShow_fromValidStates_returnsNoShow() {
        assertThat(AppointmentTransitions.noShow(AppointmentStatus.PENDING, false)).isEqualTo(AppointmentStatus.NO_SHOW);
        assertThat(AppointmentTransitions.noShow(AppointmentStatus.CONFIRMED, false)).isEqualTo(AppointmentStatus.NO_SHOW);
    }

    @Test
    void noShow_whenCheckedIn_throwsBusinessException() {
        assertThatThrownBy(() -> AppointmentTransitions.noShow(AppointmentStatus.CONFIRMED, true))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void reschedule_fromValidStates_returnsRescheduled() {
        assertThat(AppointmentTransitions.reschedule(AppointmentStatus.PENDING, false)).isEqualTo(AppointmentStatus.RESCHEDULED);
        assertThat(AppointmentTransitions.reschedule(AppointmentStatus.CONFIRMED, false)).isEqualTo(AppointmentStatus.RESCHEDULED);
    }

    @Test
    void reschedule_whenCheckedIn_throwsBusinessException() {
        assertThatThrownBy(() -> AppointmentTransitions.reschedule(AppointmentStatus.CONFIRMED, true))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void attend_fromConfirmedAndCheckedIn_returnsAttended() {
        assertThat(AppointmentTransitions.attend(AppointmentStatus.CONFIRMED, true)).isEqualTo(AppointmentStatus.ATTENDED);
    }

    @Test
    void attend_withoutCheckIn_throwsBusinessException() {
        assertThatThrownBy(() -> AppointmentTransitions.attend(AppointmentStatus.CONFIRMED, false))
                .isInstanceOf(BusinessException.class);
    }
}
