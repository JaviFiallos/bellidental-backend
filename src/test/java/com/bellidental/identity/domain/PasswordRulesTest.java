package com.bellidental.identity.domain;

import com.bellidental.shared.exception.InvalidInputException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordRulesTest {

    @Test
    void validate_shorterThanTen_throwsInvalidInput() {
        assertThatThrownBy(() -> PasswordRules.validate("corta123"))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void validate_tenOrMoreCharacters_passes() {
        assertThatCode(() -> PasswordRules.validate("diezcaracteres"))
                .doesNotThrowAnyException();
    }

    @Test
    void usernameFromEmail_mixedCase_returnsLowercaseLocalPart() {
        assertThat(PasswordRules.usernameFromEmail("Ana.Perez@X.com")).isEqualTo("ana.perez");
    }
}
