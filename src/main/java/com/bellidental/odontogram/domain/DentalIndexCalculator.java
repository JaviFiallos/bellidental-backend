package com.bellidental.odontogram.domain;

import com.bellidental.shared.enums.Dentition;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DentalIndexCalculator {

    public record IndexResult(int cpoC, int cpoP, int cpoO, int cpoTotal, int ceoC, int ceoE, int ceoO, int ceoTotal) {}
    public record Result(IndexResult indices, Map<Short, Integer> cariesSurfaces) {}

    public static Result calculate(List<OdontogramFinding> activeFindings) {
        Set<String> cpoC = new HashSet<>();
        Set<String> cpoP = new HashSet<>();
        Set<String> cpoO = new HashSet<>();

        Set<String> ceoC = new HashSet<>();
        Set<String> ceoE = new HashSet<>();
        Set<String> ceoO = new HashSet<>();

        Map<Short, Integer> cariesSurfaces = new HashMap<>();

        for (OdontogramFinding finding : activeFindings) {
            Short tooth = finding.toothCode();
            
            if ("CARIES".equals(finding.conditionCode()) && finding.surfaceCode() != null) {
                cariesSurfaces.put(tooth, cariesSurfaces.getOrDefault(tooth, 0) + 1);
            }

            if (finding.dentition() == Dentition.PERMANENT) {
                if ("C".equals(finding.cpoComponent())) cpoC.add(tooth.toString());
                if ("P".equals(finding.cpoComponent())) cpoP.add(tooth.toString());
                if ("O".equals(finding.cpoComponent())) cpoO.add(tooth.toString());
            } else if (finding.dentition() == Dentition.DECIDUOUS) {
                if ("c".equals(finding.ceoComponent())) ceoC.add(tooth.toString());
                if ("e".equals(finding.ceoComponent())) ceoE.add(tooth.toString());
                if ("o".equals(finding.ceoComponent())) ceoO.add(tooth.toString());
            }
        }

        IndexResult indices = new IndexResult(
                cpoC.size(), cpoP.size(), cpoO.size(), cpoC.size() + cpoP.size() + cpoO.size(),
                ceoC.size(), ceoE.size(), ceoO.size(), ceoC.size() + ceoE.size() + ceoO.size()
        );

        return new Result(indices, cariesSurfaces);
    }
}
