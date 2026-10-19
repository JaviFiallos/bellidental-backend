package com.bellidental.bot.api.dto;

import java.math.BigDecimal;

public record PublicPriceResponse(
        String code,
        String name,
        BigDecimal price
) {}
