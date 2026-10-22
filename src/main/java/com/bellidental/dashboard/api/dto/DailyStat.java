package com.bellidental.dashboard.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyStat(
        LocalDate date,
        long appointments,
        long completedEncounters,
        long newPatients,
        BigDecimal collected
) {}
