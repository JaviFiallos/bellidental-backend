package com.bellidental.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("application.bot")
public record BotProperties(
        String apiKey,
        String privacyNoticeVersion,
        List<String> publicTreatments,
        Long urgentSpecialtyId
) {}
