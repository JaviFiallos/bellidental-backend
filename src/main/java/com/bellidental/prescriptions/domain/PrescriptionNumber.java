package com.bellidental.prescriptions.domain;

/** Número de receta, correlativo de la clínica: R-000001. */
public final class PrescriptionNumber {

    private PrescriptionNumber() {}

    public static String format(long sequenceValue) {
        return String.format("R-%06d", sequenceValue);
    }
}
