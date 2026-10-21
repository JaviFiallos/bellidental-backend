package com.bellidental.treatments;

import java.math.BigDecimal;

public record TreatmentPriceSummary(
        String code,
        String name,
        BigDecimal price
) {}
