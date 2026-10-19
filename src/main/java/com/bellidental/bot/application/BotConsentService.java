package com.bellidental.bot.application;

import com.bellidental.bot.api.dto.ConsentRequest;
import com.bellidental.bot.api.dto.ConsentResponse;
import com.bellidental.config.BotProperties;
import com.bellidental.persistence.entity.PrivacyConsent;
import com.bellidental.persistence.repository.PrivacyConsentRepository;
import com.bellidental.shared.time.ClinicClock;
import com.bellidental.shared.validation.PhoneNumberNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BotConsentService {

    private final PrivacyConsentRepository privacyConsentRepository;
    private final BotProperties botProperties;
    private final BotIdentityService botIdentityService;
    private final ClinicClock clinicClock;

    @Transactional(readOnly = true)
    public ConsentResponse getConsent(String phone) {
        String normalizedPhone = PhoneNumberNormalizer.normalize(phone);
        String version = botProperties.privacyNoticeVersion();
        boolean accepted = privacyConsentRepository.existsByPhoneE164AndNoticeVersion(normalizedPhone, version);
        return new ConsentResponse(accepted, version);
    }

    @Transactional
    public ConsentResponse recordConsent(ConsentRequest request) {
        String normalizedPhone = PhoneNumberNormalizer.normalize(request.phone());
        if (request.patientId() != null) {
            botIdentityService.requirePatientOfPhone(normalizedPhone, request.patientId());
        }

        String version = botProperties.privacyNoticeVersion();
        if (!privacyConsentRepository.existsByPhoneE164AndNoticeVersion(normalizedPhone, version)) {
            PrivacyConsent consent = new PrivacyConsent();
            consent.setPhoneE164(normalizedPhone);
            consent.setPatientId(request.patientId());
            consent.setNoticeVersion(version);
            consent.setAcceptedAt(clinicClock.now());
            privacyConsentRepository.save(consent);
        }

        return new ConsentResponse(true, version);
    }
}
