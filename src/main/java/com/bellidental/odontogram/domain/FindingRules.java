package com.bellidental.odontogram.domain;

import com.bellidental.shared.enums.ConditionScope;
import com.bellidental.shared.exception.InvalidInputException;
import com.bellidental.shared.enums.Arch;

public class FindingRules {

    public static void validate(
            ConditionScope scope, Short gradeMin, Short gradeMax, String conditionCode,
            Arch toothArch, Short toothCode,
            Arch endToothArch, Short endToothCode,
            String surfaceCode, Short grade, String notes, boolean hasSymbol) {
            
        if (scope == ConditionScope.SURFACE) {
            if (surfaceCode == null) {
                throw new InvalidInputException("La condición requiere una cara");
            }
            if (endToothCode != null) {
                throw new InvalidInputException("La condición de cara no aplica a un rango de piezas");
            }
        } else if (scope == ConditionScope.TOOTH) {
            if (surfaceCode != null || endToothCode != null) {
                throw new InvalidInputException("La condición de pieza no aplica a caras ni rangos");
            }
        } else if (scope == ConditionScope.SPAN) {
            if (endToothCode == null) {
                throw new InvalidInputException("La condición de rango requiere una pieza final");
            }
            if (surfaceCode != null) {
                throw new InvalidInputException("La condición de rango no aplica a caras");
            }
            if (endToothArch == null || toothArch != endToothArch || toothCode.equals(endToothCode)) {
                throw new InvalidInputException("La pieza final debe ser de la misma arcada y distinta a la inicial");
            }
        }

        if (gradeMin != null && gradeMax != null) {
            if (grade == null) {
                throw new InvalidInputException("El grado es obligatorio");
            }
            if (grade < gradeMin || grade > gradeMax) {
                throw new InvalidInputException("El grado debe estar entre " + gradeMin + " y " + gradeMax);
            }
        } else {
            if (grade != null) {
                throw new InvalidInputException("La condición no acepta grado");
            }
        }

        if (!hasSymbol) {
            throw new InvalidInputException("Estado no permitido para esta condición");
        }

        if ("OTHER".equals(conditionCode)) {
            if (notes == null || notes.isBlank()) {
                throw new InvalidInputException("Las notas son obligatorias para OTHER");
            }
        }
    }
}
