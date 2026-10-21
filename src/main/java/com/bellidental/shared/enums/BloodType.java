package com.bellidental.shared.enums;

/**
 * Tipo de sangre — fundamental en procedimientos odontológicos quirúrgicos.
 */
public enum BloodType {
    O_POS("O+"),
    O_NEG("O-"),
    A_POS("A+"),
    A_NEG("A-"),
    B_POS("B+"),
    B_NEG("B-"),
    AB_POS("AB+"),
    AB_NEG("AB-");

    private final String label;

    BloodType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Busca el tipo de sangre por su etiqueta clínica (p. ej. "AB-").
     *
     * @throws IllegalArgumentException si ninguna etiqueta coincide
     */
    public static BloodType fromLabel(String label) {
        for (BloodType bloodType : values()) {
            if (bloodType.label.equals(label)) {
                return bloodType;
            }
        }
        throw new IllegalArgumentException("Etiqueta de tipo de sangre no válida: " + label);
    }
}
