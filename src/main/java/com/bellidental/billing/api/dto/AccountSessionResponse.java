package com.bellidental.billing.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AccountSessionResponse(
        UUID encounterId,
        OffsetDateTime date,
        String professionalName,
        List<String> treatments,
        BigDecimal charge,
        BigDecimal payment,
        BigDecimal runningBalance
) {}
