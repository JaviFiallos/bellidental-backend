package com.bellidental.clinical.domain;

import com.bellidental.shared.exception.InvalidInputException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Icd10QueryTest {

    @Test
    void parse_codeWithDot_returnsUppercaseCodeSearch() {
        Icd10Query query = Icd10Query.parse("k02.1");

        assertThat(query.value()).isEqualTo("K02.1");
        assertThat(query.codeSearch()).isTrue();
    }

    @Test
    void parse_textWithSpaces_returnsTrimmedTextSearch() {
        Icd10Query query = Icd10Query.parse(" caries ");

        assertThat(query.value()).isEqualTo("caries");
        assertThat(query.codeSearch()).isFalse();
    }

    @Test
    void parse_singleCharacter_throwsInvalidInput() {
        assertThatThrownBy(() -> Icd10Query.parse("k"))
                .isInstanceOf(InvalidInputException.class);
    }
}
