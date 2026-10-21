package com.bellidental.dashboard.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProfessionalStats(
        UUID professionalId,
        String name,
        long encounters,
        BigDecimal revenue
) {}
