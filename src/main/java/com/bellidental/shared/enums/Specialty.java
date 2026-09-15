package com.bellidental.shared.enums;

/**
 * Especialidades odontológicas disponibles en la clínica Bellidental.
 */
public enum Specialty {
    GENERAL("Odontología General"),
    ORTODONCIA("Ortodoncia"),
    CIRUGIA("Cirugía Oral y Maxilofacial"),
    ENDODONCIA("Endodoncia"),
    PERIODONCIA("Periodoncia"),
    PEDIATRICA("Odontología Pediátrica"),
    ESTETICA("Odontología Estética"),
    IMPLANTOLOGIA("Implantología"),
    RADIOLOGIA("Radiología Oral");

    private final String label;

    Specialty(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
