package com.bellidental.odontogram.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DoneConditionResolverTest {

    @Test
    void resolveDoneConditionCode_caries_returnsFilling() {
        assertThat(DoneConditionResolver.resolveDoneConditionCode("CARIES")).isEqualTo("FILLING");
    }

    @Test
    void resolveDoneConditionCode_sealant_returnsSameCode() {
        assertThat(DoneConditionResolver.resolveDoneConditionCode("SEALANT")).isEqualTo("SEALANT");
    }
}
