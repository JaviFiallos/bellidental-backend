package com.bellidental.billing.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AccountResponse(
        UUID patientId,
        String patientName,
        BigDecimal totalCharges,
        BigDecimal totalPayments,
        BigDecimal balance,
        List<AccountSessionResponse> sessions
) {}
