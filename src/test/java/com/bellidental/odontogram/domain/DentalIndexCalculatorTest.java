package com.bellidental.odontogram.domain;

import com.bellidental.odontogram.api.dto.DentalIndexResponse;
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

class DentalIndexCalculatorTest {

    @Test
    void piezaConDosCarasCariadasSumaUnoACpoCYDosCarasCariadas() {
        OdontogramFinding cariesO = new OdontogramFinding(
                UUID.randomUUID(), (short) 16, null, "O", 3L, "CARIES", ConditionScope.SURFACE,
                "C", "c", Dentition.PERMANENT, Arch.UPPER, ToothType.MOLAR, FindingStatus.PENDING, null, null,
                OffsetDateTime.now(), "Oclusal"
        );
        OdontogramFinding cariesM = new OdontogramFinding(
                UUID.randomUUID(), (short) 16, null, "M", 3L, "CARIES", ConditionScope.SURFACE,
                "C", "c", Dentition.PERMANENT, Arch.UPPER, ToothType.MOLAR, FindingStatus.PENDING, null, null,
                OffsetDateTime.now(), "Mesial"
        );

        DentalIndexCalculator.Result result = DentalIndexCalculator.calculate(List.of(cariesO, cariesM));

        assertThat(result.indices().cpoC()).isEqualTo(1);
        assertThat(result.indices().cpoTotal()).isEqualTo(1);
        assertThat(result.cariesSurfaces()).containsEntry((short) 16, 2);
    }

    @Test
    void extractionEnPermanenteSumaPYEnTemporalSumaE() {
        OdontogramFinding permExtraction = new OdontogramFinding(
                UUID.randomUUID(), (short) 16, null, null, 6L, "EXTRACTION", ConditionScope.TOOTH,
                "P", "e", Dentition.PERMANENT, Arch.UPPER, ToothType.MOLAR, FindingStatus.PENDING, null, null,
                OffsetDateTime.now(), null
        );
        OdontogramFinding tempExtraction = new OdontogramFinding(
                UUID.randomUUID(), (short) 54, null, null, 6L, "EXTRACTION", ConditionScope.TOOTH,
                "P", "e", Dentition.DECIDUOUS, Arch.UPPER, ToothType.MOLAR, FindingStatus.PENDING, null, null,
                OffsetDateTime.now(), null
        );

        DentalIndexCalculator.Result result = DentalIndexCalculator.calculate(List.of(permExtraction, tempExtraction));

        assertThat(result.indices().cpoP()).isEqualTo(1);
        assertThat(result.indices().cpoTotal()).isEqualTo(1);
        assertThat(result.indices().ceoE()).isEqualTo(1);
        assertThat(result.indices().ceoTotal()).isEqualTo(1);
    }
}
