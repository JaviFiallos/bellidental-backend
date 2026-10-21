package com.bellidental.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

public class MutableClock extends Clock {

    private final ZoneId zone;
    private final AtomicReference<Instant> fixedInstant;

    public MutableClock() {
        this(ZoneOffset.UTC, new AtomicReference<>(null));
    }

    private MutableClock(ZoneId zone, AtomicReference<Instant> fixedInstant) {
        this.zone = zone;
        this.fixedInstant = fixedInstant;
    }

    public void fix(Instant instant) {
        this.fixedInstant.set(instant);
    }

    public void reset() {
        this.fixedInstant.set(null);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        if (zone.equals(this.zone)) {
            return this;
        }
        return new MutableClock(zone, this.fixedInstant);
    }

    @Override
    public Instant instant() {
        Instant fixed = fixedInstant.get();
        return fixed != null ? fixed : Instant.now();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof MutableClock other) {
            return zone.equals(other.zone) && fixedInstant.equals(other.fixedInstant);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return zone.hashCode() ^ fixedInstant.hashCode();
    }
}
