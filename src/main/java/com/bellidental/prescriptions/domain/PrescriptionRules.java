package com.bellidental.prescriptions.domain;

import com.bellidental.shared.enums.PrescriptionStatus;
import com.bellidental.shared.exception.BusinessException;

public final class PrescriptionRules {

    private PrescriptionRules() {
    }

    public static void checkEditable(PrescriptionStatus status, boolean voided) {
        if (voided || status != PrescriptionStatus.DRAFT) {
            throw new BusinessException("Solo se puede modificar una receta en borrador.");
        }
    }

    public static void checkCanIssue(PrescriptionStatus status, boolean voided, long itemCount) {
        if (voided || status != PrescriptionStatus.DRAFT) {
            throw new BusinessException("Solo se puede emitir una receta en borrador.");
        }
        if (itemCount == 0) {
            throw new BusinessException("La receta debe tener al menos un medicamento.");
        }
    }

    public static void checkCanVoid(boolean voided) {
        if (voided) {
            throw new BusinessException("La receta ya está anulada.");
        }
    }

    /** Un valor enviado en la petición gana; si no llega (nulo o vacío), se copia el del preset. */
    public static String resolveItemField(String requestValue, String presetValue) {
        return requestValue == null || requestValue.isBlank() ? presetValue : requestValue;
    }
}
