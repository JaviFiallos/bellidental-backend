package com.bellidental.dashboard.api.dto;

import java.math.BigDecimal;
import java.util.Map;

public record AppointmentStats(
        long total,
        Map<String, Long> byStatus,
        BigDecimal noShowRate,
        long completedEncounters
) {}
