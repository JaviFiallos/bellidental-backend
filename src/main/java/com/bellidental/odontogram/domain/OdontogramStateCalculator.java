package com.bellidental.odontogram.domain;

import com.bellidental.shared.enums.ConditionScope;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OdontogramStateCalculator {

    public static List<OdontogramFinding> calculateState(List<OdontogramFinding> findings) {
        // Ordenamos por fecha de creación ascendente
        List<OdontogramFinding> sorted = new ArrayList<>(findings);
        sorted.sort(Comparator.comparing(OdontogramFinding::createdAt));

        Map<String, OdontogramFinding> state = new HashMap<>();

        for (OdontogramFinding finding : sorted) {
            String key = generateKey(finding);
            state.put(key, finding);
        }

        // Determinar qué piezas están "MISSING"
        Set<Short> missingTeeth = new HashSet<>();
        for (OdontogramFinding finding : state.values()) {
            if (finding.scope() == ConditionScope.TOOTH && 
               ("MISSING_CARIES".equals(finding.conditionCode()) || "MISSING_OTHER".equals(finding.conditionCode()))) {
                missingTeeth.add(finding.toothCode());
            }
        }

        List<OdontogramFinding> result = new ArrayList<>();
        for (OdontogramFinding finding : state.values()) {
            if (finding.scope() != ConditionScope.SPAN && missingTeeth.contains(finding.toothCode())) {
                if ("MISSING_CARIES".equals(finding.conditionCode()) || "MISSING_OTHER".equals(finding.conditionCode())) {
                    result.add(finding);
                }
            } else {
                result.add(finding);
            }
        }

        // Retornar ordenado nuevamente (opcional, pero buena práctica)
        result.sort(Comparator.comparing(OdontogramFinding::createdAt));
        return result;
    }

    private static String generateKey(OdontogramFinding finding) {
        if (finding.scope() == ConditionScope.SURFACE) {
            return "SURFACE:" + finding.toothCode() + ":" + finding.surfaceCode();
        } else if (finding.scope() == ConditionScope.TOOTH) {
            return "TOOTH:" + finding.toothCode() + ":" + finding.conditionId();
        } else if (finding.scope() == ConditionScope.SPAN) {
            return "SPAN:" + finding.toothCode() + ":" + finding.endToothCode() + ":" + finding.conditionId();
        }
        return finding.id().toString();
    }
}
