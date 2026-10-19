package com.bellidental.bot.api.dto;

import java.util.List;

public record ClinicInfoResponse(
        String clinicName,
        String address,
        List<String> hours,
        List<String> paymentMethods,
        String receptionWhatsapp,
        String afterHoursPhone,
        List<PublicPriceResponse> prices
) {}
