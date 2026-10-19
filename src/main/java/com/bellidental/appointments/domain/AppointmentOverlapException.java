package com.bellidental.appointments.domain;

import com.bellidental.shared.exception.BusinessException;

public class AppointmentOverlapException extends BusinessException {

    public AppointmentOverlapException(String message) {
        super(message, "APPOINTMENT_OVERLAP");
    }
}
