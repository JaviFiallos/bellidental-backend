package com.bellidental.appointments;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Horario efectivo de un profesional en un día (ya con excepciones por fecha). Vacío si no atiende. */
public record EffectiveDay(LocalDate date, List<Shift> shifts) {

    public record Shift(LocalTime startTime, LocalTime endTime) {}
}
