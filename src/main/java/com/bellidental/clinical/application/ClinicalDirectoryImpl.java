package com.bellidental.clinical.application;

import com.bellidental.appointments.AppointmentScheduler;
import com.bellidental.clinical.ClinicalDirectory;
import com.bellidental.clinical.DiagnosisSummary;
import com.bellidental.clinical.EncounterSummary;
import com.bellidental.persistence.entity.EncounterDiagnosis;
import com.bellidental.persistence.entity.Icd10Code;
import com.bellidental.persistence.repository.EncounterDiagnosisRepository;
import com.bellidental.persistence.repository.EncounterRepository;
import com.bellidental.persistence.repository.Icd10CodeRepository;
import com.bellidental.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClinicalDirectoryImpl implements ClinicalDirectory {

    private final EncounterRepository encounterRepository;
    private final EncounterDiagnosisRepository diagnosisRepository;
    private final Icd10CodeRepository icd10CodeRepository;
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

    @Override
    public Optional<DiagnosisSummary> findDiagnosis(UUID encounterDiagnosisId) {
        return diagnosisRepository.findById(encounterDiagnosisId)
                .map(this::toDiagnosisSummary);
    }

    private DiagnosisSummary toDiagnosisSummary(EncounterDiagnosis diagnosis) {
        Icd10Code icd10Code = icd10CodeRepository.findById(diagnosis.getIcd10Id())
                .orElseThrow(() -> new ResourceNotFoundException("Código CIE-10 no encontrado: " + diagnosis.getIcd10Id()));
        return new DiagnosisSummary(
                diagnosis.getId(),
                diagnosis.getEncounterId(),
                icd10Code.getCode(),
                icd10Code.getDescription(),
                diagnosis.getVoidedAt() != null
        );
    }
}
