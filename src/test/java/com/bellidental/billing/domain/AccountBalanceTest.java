package com.bellidental.billing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AccountBalanceTest {

    @Test
    void balance_chargesAndPayments_returnsDifference() {
        BigDecimal balance = AccountBalance.balance(new BigDecimal("100.00"), new BigDecimal("30.00"));

        assertThat(balance).isEqualByComparingTo("70.00");
    }

    @Test
    void balance_paymentsExceedCharges_returnsNegative() {
        BigDecimal balance = AccountBalance.balance(new BigDecimal("20.00"), new BigDecimal("35.00"));

        assertThat(balance).isEqualByComparingTo("-15.00");
    }

    @Test
    void runningBalances_twoSessions_accumulates() {
        List<BigDecimal> balances = AccountBalance.runningBalances(
                List.of(new BigDecimal("50"), new BigDecimal("20")),
                List.of(new BigDecimal("10"), BigDecimal.ZERO)
        );

        assertThat(balances).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("40.00"), new BigDecimal("60.00"));
    }
}
