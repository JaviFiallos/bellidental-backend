package com.bellidental.treatments.api.dto;

import java.math.BigDecimal;

public record TreatmentResponse(
        Long id,
        String code,
        String name,
        String description,
        BigDecimal basePrice,
        Long specialtyId,
        boolean active
) {}
