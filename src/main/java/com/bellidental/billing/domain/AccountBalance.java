package com.bellidental.billing.domain;

import com.bellidental.shared.exception.InvalidInputException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Reglas puras del estado de cuenta: saldo = cargos − abonos (puede ser negativo). */
public final class AccountBalance {

    private static final int MONEY_SCALE = 2;

    private AccountBalance() {}

    public static BigDecimal balance(BigDecimal totalCharges, BigDecimal totalPayments) {
        return totalCharges.subtract(totalPayments).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static List<BigDecimal> runningBalances(List<BigDecimal> charges, List<BigDecimal> payments) {
        if (charges.size() != payments.size()) {
            throw new InvalidInputException("Cargos y abonos deben tener el mismo número de sesiones");
        }
        List<BigDecimal> runningBalances = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        for (int i = 0; i < charges.size(); i++) {
            running = running.add(charges.get(i)).subtract(payments.get(i));
            runningBalances.add(running.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        }
        return runningBalances;
    }
}
