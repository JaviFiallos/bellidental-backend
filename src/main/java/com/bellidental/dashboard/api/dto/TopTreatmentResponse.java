package com.bellidental.dashboard.api.dto;

import java.math.BigDecimal;

public record TopTreatmentResponse(
        String code,
        String name,
        long quantity,
        BigDecimal amount
) {}
