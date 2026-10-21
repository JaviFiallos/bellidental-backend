package com.bellidental.shared.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EcuadorianIdValidatorTest {

    @Test
    void isValid_validId_returnsTrue() {
        // Cédula válida ficticia (17 es Pichincha) - OJO: 1710034065 -> 1*2+7*1+1*2+0*1+0*2+3*1+4*2+0*1+6*2 = 2+7+2+0+0+3+8+0+12-9=3 -> 25. 25%10=5, 10-5=5. So 1710034065 is valid.
        assertThat(EcuadorianIdValidator.isValid("1710034065")).isTrue();
    }

    @Test
    void isValid_wrongCheckDigit_returnsFalse() {
        assertThat(EcuadorianIdValidator.isValid("1710034066")).isFalse();
    }

    @Test
    void isValid_invalidProvince_returnsFalse() {
        // 99 is invalid province
        assertThat(EcuadorianIdValidator.isValid("9910034065")).isFalse();
    }

    @Test
    void isValid_invalidThirdDigit_returnsFalse() {
        // 176... third digit is 6
        assertThat(EcuadorianIdValidator.isValid("1760034065")).isFalse();
    }

    @Test
    void isValid_invalidLength_returnsFalse() {
        assertThat(EcuadorianIdValidator.isValid("171003406")).isFalse(); // 9 chars
        assertThat(EcuadorianIdValidator.isValid("17100340651")).isFalse(); // 11 chars
    }

    @Test
    void isValid_null_returnsFalse() {
        assertThat(EcuadorianIdValidator.isValid(null)).isFalse();
    }
}
