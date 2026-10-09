package com.bellidental.shared.time;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Component
public class ClinicClock {

    private final Clock clock;
    private final String clinicZone;

    public ClinicClock(Clock clock, @Value("${application.clinic.zone}") String clinicZone) {
        this.clock = clock;
        this.clinicZone = clinicZone;
    }

    public ZoneId zone() {
        return ZoneId.of(clinicZone);
    }

    public OffsetDateTime now() {
        return OffsetDateTime.now(clock.withZone(zone()));
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone()));
    }
}
