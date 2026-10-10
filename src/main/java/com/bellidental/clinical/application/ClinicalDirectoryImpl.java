package com.bellidental.clinical.application;

import com.bellidental.appointments.AppointmentScheduler;
import com.bellidental.clinical.ClinicalDirectory;
import com.bellidental.clinical.EncounterSummary;
import com.bellidental.persistence.repository.EncounterRepository;
import com.bellidental.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClinicalDirectoryImpl implements ClinicalDirectory {

    private final EncounterRepository encounterRepository;
    private final EncounterService encounterService;
    private final AppointmentScheduler appointmentScheduler;

    @Override
    public Optional<EncounterSummary> findEncounter(UUID encounterId) {
        return encounterRepository.findById(encounterId)
                .map(encounter -> {
                    UUID patientId = appointmentScheduler.findById(encounter.getAppointmentId())
                            .map(a -> a.patientId())
                            .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada: " + encounter.getAppointmentId()));
                    return encounterService.toSummary(encounter, patientId);
                });
    }

    @Override
    public EncounterSummary requireModifiableEncounter(UUID encounterId) {
        return encounterService.requireModifiableEncounter(encounterId);
    }
}
