package com.bellidental.bot.application;

import com.bellidental.bot.api.dto.ClinicInfoResponse;
import com.bellidental.bot.api.dto.PublicPriceResponse;
import com.bellidental.config.BotProperties;
import com.bellidental.config.ClinicProperties;
import com.bellidental.treatments.TreatmentDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BotInfoService {

    private final ClinicProperties clinicProperties;
    private final BotProperties botProperties;
    private final TreatmentDirectory treatmentDirectory;

    public ClinicInfoResponse getClinicInfo() {
        List<String> codes = botProperties.publicTreatments() != null
                ? botProperties.publicTreatments()
                : Collections.emptyList();

        List<PublicPriceResponse> prices = treatmentDirectory.findActiveByCodes(codes).stream()
                .map(t -> new PublicPriceResponse(t.code(), t.name(), t.price()))
                .toList();

        return new ClinicInfoResponse(
                clinicProperties.name(),
                clinicProperties.address(),
                clinicProperties.hours(),
                clinicProperties.paymentMethods(),
                clinicProperties.receptionWhatsapp(),
                clinicProperties.afterHoursPhone(),
                prices
        );
    }
}
