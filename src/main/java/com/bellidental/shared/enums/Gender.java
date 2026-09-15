package com.bellidental.shared.enums;

/**
 * Género biológico/identidad de género del paciente o profesional.
 */
public enum Gender {
    MASCULINO("Masculino"),
    FEMENINO("Femenino"),
    OTRO("Otro"),
    PREFIERO_NO_DECIR("Prefiero no decir");

    private final String label;

    Gender(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
