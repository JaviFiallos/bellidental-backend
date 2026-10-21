package com.bellidental.billing.api.dto;

import com.bellidental.shared.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID patientId,
        UUID encounterId,
        BigDecimal amount,
        PaymentMethod method,
        String receiptNumber,
        OffsetDateTime paidAt,
        UUID receivedBy,
        boolean voided,
        String voidReason
) {}
