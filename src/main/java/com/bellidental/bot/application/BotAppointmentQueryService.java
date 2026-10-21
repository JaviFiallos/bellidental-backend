package com.bellidental.bot.application;

import com.bellidental.appointments.AppointmentScheduler;
import com.bellidental.appointments.AppointmentSummary;
import com.bellidental.bot.api.dto.BotAppointmentResponse;
import com.bellidental.professionals.ProfessionalDirectory;
import com.bellidental.professionals.SpecialtySummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BotAppointmentQueryService {

    private final AppointmentScheduler appointmentScheduler;
    private final BotIdentityService botIdentityService;
    private final ProfessionalDirectory professionalDirectory;

    public List<BotAppointmentResponse> getAppointmentsByPatientAndPhone(String phone, UUID patientId) {
        botIdentityService.requirePatientOfPhone(phone, patientId);

        List<AppointmentSummary> appointments = appointmentScheduler.findActiveByPatient(patientId);

        return appointments.stream()
                .map(a -> {
                    String specialtyName = professionalDirectory.findSpecialty(a.specialtyId())
                            .map(SpecialtySummary::name)
                            .orElse("");
                    String professionalName = professionalDirectory.findById(a.professionalId())
                            .map(p -> (p.firstName() + " " + p.lastName()).trim())
                            .orElse("");
                    return new BotAppointmentResponse(
                            a.id(),
                            a.specialtyId(),
                            specialtyName,
                            a.professionalId(),
                            professionalName,
                            a.startAt(),
                            a.status()
                    );
                })
                .toList();
    }
}
