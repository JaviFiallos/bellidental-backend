package com.bellidental.odontogram.domain;

import com.bellidental.shared.enums.Arch;
import com.bellidental.shared.enums.ToothType;

public class SurfaceLabelResolver {

    public static String resolveLabel(String surfaceCode, Arch arch, ToothType toothType, String surfaceName) {
        if (surfaceCode == null) {
            return null;
        }
        return switch (surfaceCode) {
            case "L" -> arch == Arch.UPPER ? "Palatina" : "Lingual";
            case "O" -> (toothType == ToothType.INCISOR || toothType == ToothType.CANINE) ? "Incisal" : "Oclusal";
            case "V", "M", "D" -> surfaceName != null ? surfaceName : surfaceCode;
            default -> surfaceName != null ? surfaceName : surfaceCode;
        };
    }
}
