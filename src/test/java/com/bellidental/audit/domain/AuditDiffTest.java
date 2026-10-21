package com.bellidental.audit.domain;

import com.bellidental.audit.domain.AuditDiff.FieldChange;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditDiffTest {

    @Test
    void diff_changedField_returnsOldAndNew() {
        Map<String, String> before = Map.of("chiefComplaint", "Consulta inicial", "status", "OPEN");
        Map<String, String> after = Map.of("chiefComplaint", "Dolor en molar", "status", "OPEN");

        assertThat(AuditDiff.diff(before, after))
                .containsExactly(new FieldChange("chiefComplaint", "Consulta inicial", "Dolor en molar"));
    }

    @Test
    void diff_equalMaps_returnsEmpty() {
        Map<String, String> values = Map.of("chiefComplaint", "Consulta inicial");

        assertThat(AuditDiff.diff(values, Map.copyOf(values))).isEmpty();
    }
}
