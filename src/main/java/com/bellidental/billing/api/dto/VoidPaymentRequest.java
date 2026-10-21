package com.bellidental.billing.api.dto;

import jakarta.validation.constraints.NotBlank;

public record VoidPaymentRequest(
        @NotBlank String voidReason
) {}
