package com.bellidental.shared.validation;

import com.bellidental.shared.exception.InvalidInputException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumberNormalizerTest {

    @Test
    void normalize_localWithZero_returnsE164() {
        assertThat(PhoneNumberNormalizer.normalize("0987654321")).isEqualTo("593987654321");
        assertThat(PhoneNumberNormalizer.normalize("022345678")).isEqualTo("59322345678"); // Fijo de quito
    }

    @Test
    void normalize_withPlusAndSpaces_returnsE164() {
        assertThat(PhoneNumberNormalizer.normalize("+593 98 765 4321")).isEqualTo("593987654321");
        assertThat(PhoneNumberNormalizer.normalize("(+593) 98-765-4321")).isEqualTo("593987654321");
    }

    @Test
    void normalize_alreadyNormalized_returnsSame() {
        assertThat(PhoneNumberNormalizer.normalize("593987654321")).isEqualTo("593987654321");
    }

    @Test
    void normalize_nullOrBlank_returnsNull() {
        assertThat(PhoneNumberNormalizer.normalize(null)).isNull();
        assertThat(PhoneNumberNormalizer.normalize("  ")).isNull();
    }

    @Test
    void normalize_invalid_throwsInvalidInputException() {
        assertThatThrownBy(() -> PhoneNumberNormalizer.normalize("123"))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("Formato de teléfono no reconocible");

        assertThatThrownBy(() -> PhoneNumberNormalizer.normalize("hola"))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("Formato de teléfono no reconocible");
    }
}
