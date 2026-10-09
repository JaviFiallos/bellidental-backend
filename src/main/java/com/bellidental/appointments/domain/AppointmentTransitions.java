package com.bellidental.appointments.domain;

import com.bellidental.shared.enums.AppointmentStatus;
import com.bellidental.shared.exception.BusinessException;

public class AppointmentTransitions {

    public static AppointmentStatus confirm(AppointmentStatus currentStatus) {
        if (currentStatus != AppointmentStatus.PENDING) {
            throw new BusinessException("Solo se pueden confirmar citas en estado PENDING.");
        }
        return AppointmentStatus.CONFIRMED;
    }

    public static AppointmentStatus cancel(AppointmentStatus currentStatus) {
        if (currentStatus != AppointmentStatus.PENDING && currentStatus != AppointmentStatus.CONFIRMED) {
            throw new BusinessException("Solo se pueden cancelar citas en estado PENDING o CONFIRMED.");
        }
        return AppointmentStatus.CANCELLED;
    }

    public static AppointmentStatus checkIn(AppointmentStatus currentStatus, boolean hasCheckIn) {
        if (currentStatus != AppointmentStatus.PENDING && currentStatus != AppointmentStatus.CONFIRMED) {
            throw new BusinessException("Solo se puede hacer check-in en citas PENDING o CONFIRMED.");
        }
        if (hasCheckIn) {
            throw new BusinessException("La cita ya tiene check-in.");
        }
        return currentStatus;
    }

    public static AppointmentStatus noShow(AppointmentStatus currentStatus, boolean hasCheckIn) {
        if (currentStatus != AppointmentStatus.PENDING && currentStatus != AppointmentStatus.CONFIRMED) {
            throw new BusinessException("Solo se puede marcar no-show en citas PENDING o CONFIRMED.");
        }
        if (hasCheckIn) {
            throw new BusinessException("No se puede marcar no-show en una cita con check-in.");
        }
        return AppointmentStatus.NO_SHOW;
    }

    public static AppointmentStatus reschedule(AppointmentStatus currentStatus, boolean hasCheckIn) {
        if (currentStatus != AppointmentStatus.PENDING && currentStatus != AppointmentStatus.CONFIRMED) {
            throw new BusinessException("Solo se puede reprogramar citas en estado PENDING o CONFIRMED.");
        }
        if (hasCheckIn) {
            throw new BusinessException("No se puede reprogramar una cita con check-in.");
        }
        return AppointmentStatus.RESCHEDULED;
    }

    public static AppointmentStatus attend(AppointmentStatus currentStatus, boolean hasCheckIn) {
        if (currentStatus != AppointmentStatus.PENDING && currentStatus != AppointmentStatus.CONFIRMED) {
            throw new BusinessException("Estado inválido para atender cita.");
        }
        if (!hasCheckIn) {
            throw new BusinessException("La cita debe tener check-in para ser atendida.");
        }
        return AppointmentStatus.ATTENDED;
    }
}
