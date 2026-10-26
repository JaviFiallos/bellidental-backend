package com.bellidental.dashboard.domain;

import com.bellidental.shared.exception.InvalidInputException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoShowRateTest {

    @Test
    void of_twoOfEight_returns25Point0() {
        assertThat(NoShowRate.of(2, 8)).isEqualByComparingTo(new BigDecimal("25.0"));
    }

    @Test
    void of_noPastAppointments_returnsZero() {
        assertThat(NoShowRate.of(0, 0)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void of_moreNoShowsThanPast_throwsInvalidInput() {
        assertThatThrownBy(() -> NoShowRate.of(3, 2)).isInstanceOf(InvalidInputException.class);
    }
}
