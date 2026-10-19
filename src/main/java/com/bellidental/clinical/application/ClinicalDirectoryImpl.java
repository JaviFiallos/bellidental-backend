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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    @Override
    public List<EncounterSummary> findPatientEncounters(UUID patientId) {
        return encounterRepository.findActiveByPatientIdOrderByStartAtAsc(patientId).stream()
                .map(encounter -> encounterService.toSummary(encounter, patientId))
                .toList();
    }

    @Override
    public List<DiagnosisSummary> findDiagnoses(Collection<UUID> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return List.of();
        }
        List<EncounterDiagnosis> diagnoses = diagnosisRepository
                .findByEncounterIdInAndVoidedAtIsNullOrderByCreatedAtAsc(encounterIds);
        if (diagnoses.isEmpty()) {
            return List.of();
        }
        Set<Long> icd10Ids = diagnoses.stream()
                .map(EncounterDiagnosis::getIcd10Id)
                .collect(Collectors.toSet());
        Map<Long, Icd10Code> icd10Map = icd10CodeRepository.findAllById(icd10Ids).stream()
                .collect(Collectors.toMap(Icd10Code::getId, Function.identity()));
        return diagnoses.stream()
                .map(d -> {
                    Icd10Code code = icd10Map.get(d.getIcd10Id());
                    return new DiagnosisSummary(
                            d.getId(),
                            d.getEncounterId(),
                            code != null ? code.getCode() : "",
                            code != null ? code.getDescription() : "",
                            false,
                            d.getCertainty(),
                            d.getNotes()
                    );
                })
                .toList();
    }

    private DiagnosisSummary toDiagnosisSummary(EncounterDiagnosis diagnosis) {
        Icd10Code icd10Code = icd10CodeRepository.findById(diagnosis.getIcd10Id())
                .orElseThrow(() -> new ResourceNotFoundException("Código CIE-10 no encontrado: " + diagnosis.getIcd10Id()));
        return new DiagnosisSummary(
                diagnosis.getId(),
                diagnosis.getEncounterId(),
                icd10Code.getCode(),
                icd10Code.getDescription(),
                diagnosis.getVoidedAt() != null,
                diagnosis.getCertainty(),
                diagnosis.getNotes()
        );
    }
}
