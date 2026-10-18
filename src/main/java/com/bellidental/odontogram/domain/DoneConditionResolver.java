package com.bellidental.odontogram.domain;

/** Condición que representa el estado realizado de un hallazgo pendiente. */
public final class DoneConditionResolver {

    private static final String CARIES = "CARIES";
    private static final String FILLING = "FILLING";
    private static final String EXTRACTION = "EXTRACTION";
    private static final String MISSING_CARIES = "MISSING_CARIES";

    private DoneConditionResolver() {
    }

    public static String resolveDoneConditionCode(String pendingCode) {
        return switch (pendingCode) {
            case CARIES -> FILLING;
            case EXTRACTION -> MISSING_CARIES;
            default -> pendingCode;
        };
    }
}
