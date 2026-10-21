package com.bellidental.shared.enums;

/**
 * Tipos de documento de identidad aceptados en Ecuador.
 */
public enum DocumentType {
    CI("Cédula de Identidad"),
    RUC("Registro Único de Contribuyentes"),
    PASAPORTE("Pasaporte");

    private final String label;

    DocumentType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
