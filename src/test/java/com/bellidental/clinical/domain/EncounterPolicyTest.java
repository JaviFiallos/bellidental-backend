package com.bellidental.clinical.domain;

import com.bellidental.shared.enums.AppointmentStatus;
import com.bellidental.shared.enums.EncounterStatus;
import com.bellidental.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncounterPolicyTest {

    @Test
    void checkCanOpen_withoutCheckedIn_throwsBusinessException() {
        assertThatThrownBy(() ->
                EncounterPolicy.checkCanOpen(AppointmentStatus.CONFIRMED, null, false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void checkCanModify_differentProfessional_throwsBusinessException() {
        UUID attendingProfessional = UUID.randomUUID();
        UUID currentProfessional = UUID.randomUUID();

        assertThatThrownBy(() ->
                EncounterPolicy.checkCanModify(attendingProfessional, currentProfessional, false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void checkCanComplete_withoutActiveDiagnoses_throwsBusinessException() {
        assertThatThrownBy(() ->
                EncounterPolicy.checkCanComplete(EncounterStatus.OPEN, 0))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void checkCanComplete_alreadyCompleted_throwsBusinessException() {
        assertThatThrownBy(() ->
                EncounterPolicy.checkCanComplete(EncounterStatus.COMPLETED, 1))
                .isInstanceOf(BusinessException.class);
    }
}
