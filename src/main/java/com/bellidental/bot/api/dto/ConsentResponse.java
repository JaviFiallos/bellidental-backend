package com.bellidental.bot.api.dto;

public record ConsentResponse(
        boolean accepted,
        String noticeVersion
) {}
