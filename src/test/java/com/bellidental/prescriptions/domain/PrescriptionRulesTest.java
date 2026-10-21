package com.bellidental.prescriptions.domain;

import com.bellidental.shared.enums.PrescriptionStatus;
import com.bellidental.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrescriptionRulesTest {

    @Test
    void resolveItemField_requestNull_copiesPresetValue() {
        assertThat(PrescriptionRules.resolveItemField(null, "cada 8 horas")).isEqualTo("cada 8 horas");
    }

    @Test
    void resolveItemField_requestValue_overridesPreset() {
        assertThat(PrescriptionRules.resolveItemField("cada 12 horas", "cada 8 horas")).isEqualTo("cada 12 horas");
    }

    @Test
    void checkCanIssue_draftWithItems_passes() {
        PrescriptionRules.checkCanIssue(PrescriptionStatus.DRAFT, false, 2);
    }

    @Test
    void checkEditable_issued_throwsBusinessException() {
        assertThatThrownBy(() -> PrescriptionRules.checkEditable(PrescriptionStatus.ISSUED, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Solo se puede modificar una receta en borrador.");
    }
}
