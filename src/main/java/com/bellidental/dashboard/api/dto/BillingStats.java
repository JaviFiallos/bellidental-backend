package com.bellidental.dashboard.api.dto;

import java.math.BigDecimal;
import java.util.Map;

public record BillingStats(
        BigDecimal collected,
        Map<String, BigDecimal> byMethod,
        BigDecimal outstandingBalance
) {}
