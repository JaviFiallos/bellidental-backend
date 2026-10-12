package com.bellidental.persistence.entity;

import com.bellidental.shared.enums.BloodType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Guarda la etiqueta clínica (p. ej. "AB-") en la columna {@code blood_type} varchar(3),
 * en lugar del nombre del enum (p. ej. "AB_NEG").
 */
@Converter
public class BloodTypeConverter implements AttributeConverter<BloodType, String> {

    @Override
    public String convertToDatabaseColumn(BloodType bloodType) {
        return bloodType == null ? null : bloodType.getLabel();
    }

    @Override
    public BloodType convertToEntityAttribute(String label) {
        return label == null ? null : BloodType.fromLabel(label);
    }
}
