package com.bellidental.dashboard.api.dto;

import java.time.LocalDate;
import java.util.List;

public record DashboardSummaryResponse(
        LocalDate from,
        LocalDate to,
        PatientStats patients,
        AppointmentStats appointments,
        BillingStats billing,
        List<TopTreatmentResponse> topTreatments,
        List<ProfessionalStats> byProfessional,
        List<ChannelStat> byChannel
) {}
