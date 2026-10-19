package com.bellidental.bot.api.dto;

import java.util.List;

public record UrgentSlotsResponse(
        Long specialtyId,
        boolean noSlotsToday,
        List<BotSlotResponse> slots
) {}
