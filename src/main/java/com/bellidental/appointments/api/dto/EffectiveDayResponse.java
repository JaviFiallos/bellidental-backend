package com.bellidental.appointments.api.dto;

import com.bellidental.appointments.domain.Shift;

import java.time.LocalDate;
import java.util.List;

public record EffectiveDayResponse(
    LocalDate date,
    List<Shift> shifts
) {
}
