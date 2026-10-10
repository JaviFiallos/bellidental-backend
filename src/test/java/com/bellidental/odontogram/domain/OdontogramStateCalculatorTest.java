package com.bellidental.odontogram.domain;

import com.bellidental.shared.enums.Arch;
import com.bellidental.shared.enums.ConditionScope;
import com.bellidental.shared.enums.Dentition;
import com.bellidental.shared.enums.FindingStatus;
import com.bellidental.shared.enums.ToothType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OdontogramStateCalculatorTest {

    @Test
    void elUltimoHallazgoGanaPorPiezaYCara() {
        OdontogramFinding caries = new OdontogramFinding(
                UUID.randomUUID(), (short) 16, null, "O", 3L, "CARIES", ConditionScope.SURFACE,
                "C", "c", Dentition.PERMANENT, Arch.UPPER, ToothType.MOLAR, FindingStatus.PENDING, null, null,
                OffsetDateTime.now().minusDays(1), "Oclusal"
        );
        OdontogramFinding filling = new OdontogramFinding(
                UUID.randomUUID(), (short) 16, null, "O", 4L, "FILLING", ConditionScope.SURFACE,
                "O", "o", Dentition.PERMANENT, Arch.UPPER, ToothType.MOLAR, FindingStatus.DONE, null, null,
                OffsetDateTime.now(), "Oclusal"
        );

        List<OdontogramFinding> state = OdontogramStateCalculator.calculateState(List.of(caries, filling));

        assertThat(state).hasSize(1);
        assertThat(state.get(0).conditionCode()).isEqualTo("FILLING");
    }

    @Test
    void missingCariesOcultaCariesPeroNoSpan() {
        OdontogramFinding caries = new OdontogramFinding(
                UUID.randomUUID(), (short) 16, null, "O", 3L, "CARIES", ConditionScope.SURFACE,
                "C", "c", Dentition.PERMANENT, Arch.UPPER, ToothType.MOLAR, FindingStatus.PENDING, null, null,
                OffsetDateTime.now().minusDays(2), "Oclusal"
        );
        OdontogramFinding missing = new OdontogramFinding(
                UUID.randomUUID(), (short) 16, null, null, 7L, "MISSING_CARIES", ConditionScope.TOOTH,
                "P", null, Dentition.PERMANENT, Arch.UPPER, ToothType.MOLAR, FindingStatus.DONE, null, null,
                OffsetDateTime.now().minusDays(1), null
        );
        OdontogramFinding span = new OdontogramFinding(
                UUID.randomUUID(), (short) 16, (short) 26, null, 11L, "FIXED_PROSTHESIS", ConditionScope.SPAN,
                null, null, Dentition.PERMANENT, Arch.UPPER, ToothType.MOLAR, FindingStatus.DONE, null, null,
                OffsetDateTime.now(), null
        );

        List<OdontogramFinding> state = OdontogramStateCalculator.calculateState(List.of(caries, missing, span));

        assertThat(state).hasSize(2);
        assertThat(state.stream().map(OdontogramFinding::conditionCode))
                .containsExactlyInAnyOrder("MISSING_CARIES", "FIXED_PROSTHESIS");
    }
}
