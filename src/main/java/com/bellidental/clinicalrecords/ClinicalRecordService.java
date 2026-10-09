package com.bellidental.clinicalrecords;

import com.bellidental.shared.enums.HistoryScope;
import com.bellidental.patients.PatientDirectory;
import com.bellidental.patients.PatientSummary;
import com.bellidental.persistence.entity.ClinicalRecord;
import com.bellidental.persistence.entity.MedicalHistoryCondition;
import com.bellidental.persistence.entity.MedicalHistoryEntry;
import com.bellidental.persistence.entity.Patient;
import com.bellidental.persistence.entity.Professional;
import com.bellidental.persistence.repository.ClinicalRecordRepository;
import com.bellidental.persistence.repository.MedicalHistoryConditionRepository;
import com.bellidental.persistence.repository.MedicalHistoryEntryRepository;
import com.bellidental.persistence.repository.PatientRepository;
import com.bellidental.persistence.repository.ProfessionalRepository;
import com.bellidental.shared.exception.BusinessException;
import com.bellidental.shared.exception.InvalidInputException;
import com.bellidental.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClinicalRecordService {

    private static final Long OTHER_CONDITION_ID = 10L;

    private final ClinicalRecordRepository clinicalRecordRepository;
    private final MedicalHistoryConditionRepository conditionRepository;
    private final MedicalHistoryEntryRepository entryRepository;
    private final PatientRepository patientRepository;
    private final ProfessionalRepository professionalRepository;
    private final PatientDirectory patientDirectory;

    @Value("${application.clinic.zone}")
    private String clinicZone;

    private ZoneId getZone() {
        return ZoneId.of(clinicZone);
    }

    private ClinicalRecordSummary toSummary(ClinicalRecord record) {
        return new ClinicalRecordSummary(record.getId(), record.getPatient().getId(), record.getRecordNumber());
    }

    private ClinicalRecordResponse toResponse(ClinicalRecord record, List<MedicalHistoryEntry> entries) {
        List<MedicalHistoryEntryResponse> entryResponses = entries.stream()
                .map(MedicalHistoryEntryResponse::from)
                .toList();
        return ClinicalRecordResponse.from(record, entryResponses);
    }

    @Transactional
    public ClinicalRecordResponse open(UUID patientId, UUID professionalId) {
        patientDirectory.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente", patientId));

        if (professionalId == null) {
            throw new BusinessException("El usuario actual no tiene un perfil de profesional asociado.");
        }

        if (clinicalRecordRepository.findByPatientId(patientId).isPresent()) {
            throw new BusinessException("El paciente ya tiene una historia clínica abierta.");
        }

        Long recordSequenceValue = clinicalRecordRepository.getNextRecordNumber();
        String recordNumber = String.format("HC-%06d", recordSequenceValue);

        ClinicalRecord record = ClinicalRecord.builder()
                .patient(patientRepository.getReferenceById(patientId))
                .recordNumber(recordNumber)
                .openedAt(LocalDate.now(getZone()))
                .openedBy(professionalRepository.getReferenceById(professionalId))
                .noKnownHistory(false)
                .build();

        record = clinicalRecordRepository.save(record);
        return ClinicalRecordResponse.from(record, List.of());
    }

    @Transactional
    public ClinicalRecordSummary openIfAbsent(UUID patientId, UUID professionalId) {
        Optional<ClinicalRecord> existing = clinicalRecordRepository.findByPatientId(patientId);
        if (existing.isPresent()) {
            ClinicalRecord clinicalRecord = existing.get();
            return toSummary(clinicalRecord);
        }

        ClinicalRecordResponse response = open(patientId, professionalId);
        return new ClinicalRecordSummary(response.id(), response.patientId(), response.recordNumber());
    }

    @Transactional(readOnly = true)
    public Optional<ClinicalRecordSummary> findSummaryByPatientId(UUID patientId) {
        return clinicalRecordRepository.findByPatientId(patientId)
                .map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public ClinicalRecordResponse getByPatientId(UUID patientId, boolean includeVoided) {
        ClinicalRecord record = clinicalRecordRepository.findByPatientId(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Historia clínica del paciente", patientId));

        List<MedicalHistoryEntry> entries = includeVoided
                ? entryRepository.findByClinicalRecordIdOrderByConditionDisplayOrderAsc(record.getId())
                : entryRepository.findByClinicalRecordIdAndActiveTrueOrderByConditionDisplayOrderAsc(record.getId());

        return toResponse(record, entries);
    }

    @Transactional
    public ClinicalRecordResponse update(UUID id, UpdateClinicalRecordRequest request) {
        ClinicalRecord record = clinicalRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Historia clínica", id));

        if (request.noKnownHistory() != null) {
            if (request.noKnownHistory() && entryRepository.existsByClinicalRecordIdAndActiveTrue(id)) {
                throw new BusinessException("No se puede marcar como sin antecedentes mientras haya antecedentes activos.");
            }
            record.setNoKnownHistory(request.noKnownHistory());
        }

        if (request.currentMedication() != null) {
            record.setCurrentMedication(request.currentMedication());
        }

        record = clinicalRecordRepository.save(record);

        List<MedicalHistoryEntry> entries = entryRepository.findByClinicalRecordIdAndActiveTrueOrderByConditionDisplayOrderAsc(record.getId());

        return toResponse(record, entries);
    }

    @Transactional(readOnly = true)
    public List<MedicalHistoryConditionResponse> getActiveConditions() {
        return conditionRepository.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(MedicalHistoryConditionResponse::from)
                .toList();
    }

    @Transactional
    public MedicalHistoryEntryResponse addEntry(UUID recordId, CreateMedicalHistoryEntryRequest request) {
        ClinicalRecord record = clinicalRecordRepository.findById(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("Historia clínica", recordId));

        MedicalHistoryCondition condition = conditionRepository.findById(request.conditionId())
                .filter(MedicalHistoryCondition::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Condición de antecedente", request.conditionId()));

        if (request.scope() == HistoryScope.FAMILY) {
            if (request.relative() == null || request.relative().isBlank()) {
                throw new InvalidInputException("El antecedente familiar requiere especificar el familiar (relative).");
            }
        } else if (request.scope() == HistoryScope.PERSONAL) {
            if (request.relative() != null && !request.relative().isBlank()) {
                throw new InvalidInputException("El antecedente personal no debe incluir un familiar (relative).");
            }
        }

        if (OTHER_CONDITION_ID.equals(condition.getId())) {
            if (request.description() == null || request.description().isBlank()) {
                throw new InvalidInputException("La condición 'Otro' requiere una descripción.");
            }
        }

        MedicalHistoryEntry entry = MedicalHistoryEntry.builder()
                .clinicalRecord(record)
                .condition(condition)
                .scope(request.scope())
                .relative(request.relative())
                .description(request.description())
                .active(true)
                .build();

        entry = entryRepository.save(entry);

        if (record.isNoKnownHistory()) {
            record.setNoKnownHistory(false);
            clinicalRecordRepository.save(record);
        }

        return MedicalHistoryEntryResponse.from(entry);
    }

    @Transactional
    public void voidEntry(UUID entryId) {
        MedicalHistoryEntry entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("Antecedente médico", entryId));

        if (!entry.isActive()) {
            throw new BusinessException("El antecedente ya se encuentra anulado.");
        }

        entry.setActive(false);
        entry.setVoidedAt(OffsetDateTime.now());
        entryRepository.save(entry);
    }
}
