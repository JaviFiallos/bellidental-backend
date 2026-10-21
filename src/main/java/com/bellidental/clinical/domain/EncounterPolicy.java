package com.bellidental.clinical.domain;

import com.bellidental.shared.enums.AppointmentStatus;
import com.bellidental.shared.enums.EncounterStatus;
import com.bellidental.shared.exception.BusinessException;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Reglas puras de negocio para el ciclo de vida de una atención clínica. */
public class EncounterPolicy {

    private EncounterPolicy() {}

    public static void checkCanOpen(AppointmentStatus status,
                                    OffsetDateTime checkedInAt,
                                    boolean encounterExists) {
        if (status != AppointmentStatus.PENDING && status != AppointmentStatus.CONFIRMED) {
            throw new BusinessException("La cita debe estar en estado PENDING o CONFIRMED para abrir una atención");
        }
        if (checkedInAt == null) {
            throw new BusinessException("La cita debe tener check-in antes de abrir una atención");
        }
        if (encounterExists) {
            throw new BusinessException("La cita ya tiene una atención registrada");
        }
    }

    public static void checkCanModify(UUID attendingProfessionalId,
                                      UUID currentProfessionalId,
                                      boolean voided) {
        if (voided) {
            throw new BusinessException("La atención está anulada y no puede modificarse");
        }
        if (!attendingProfessionalId.equals(currentProfessionalId)) {
            throw new BusinessException("Solo el odontólogo que atendió puede modificar la atención");
        }
    }

    public static void checkCanComplete(EncounterStatus status, long activeDiagnoses) {
        if (status != EncounterStatus.OPEN) {
            throw new BusinessException("Solo se puede completar una atención en estado OPEN");
        }
        if (activeDiagnoses < 1) {
            throw new BusinessException("La atención debe tener al menos un diagnóstico activo para completarse");
        }
    }
}
