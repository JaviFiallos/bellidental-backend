package com.bellidental.dashboard.api.dto;

public record PatientStats(
        long totalActive,
        long newInRange
) {}
