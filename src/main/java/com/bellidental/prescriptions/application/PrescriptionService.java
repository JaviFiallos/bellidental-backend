package com.bellidental.prescriptions.application;

import com.bellidental.clinical.ClinicalDirectory;
import com.bellidental.clinical.DiagnosisSummary;
import com.bellidental.persistence.entity.Medication;
import com.bellidental.persistence.entity.MedicationPreset;
import com.bellidental.persistence.entity.Prescription;
import com.bellidental.persistence.entity.PrescriptionItem;
import com.bellidental.persistence.repository.MedicationPresetRepository;
import com.bellidental.persistence.repository.MedicationRepository;
import com.bellidental.persistence.repository.PrescriptionItemRepository;
import com.bellidental.persistence.repository.PrescriptionRepository;
import com.bellidental.prescriptions.api.dto.PrescriptionItemRequest;
import com.bellidental.prescriptions.api.dto.PrescriptionItemResponse;
import com.bellidental.prescriptions.api.dto.PrescriptionRequest;
import com.bellidental.prescriptions.api.dto.PrescriptionResponse;
import com.bellidental.prescriptions.domain.PrescriptionRules;
import com.bellidental.shared.enums.PrescriptionStatus;
import com.bellidental.shared.exception.BusinessException;
import com.bellidental.shared.exception.InvalidInputException;
import com.bellidental.shared.exception.ResourceNotFoundException;
import com.bellidental.shared.time.ClinicClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final MedicationRepository medicationRepository;
    private final MedicationPresetRepository medicationPresetRepository;
    private final ClinicalDirectory clinicalDirectory;
    private final ClinicClock clinicClock;

    @Transactional
    public PrescriptionResponse create(UUID encounterId, PrescriptionRequest request) {
        clinicalDirectory.requireModifiableEncounter(encounterId);
        requireDiagnosisOfEncounter(request.encounterDiagnosisId(), encounterId);

        OffsetDateTime now = clinicClock.now();
        Prescription prescription = new Prescription();
        prescription.setEncounterId(encounterId);
        prescription.setEncounterDiagnosisId(request.encounterDiagnosisId());
        prescription.setGeneralInstructions(request.generalInstructions());
        prescription.setStatus(PrescriptionStatus.DRAFT);
        prescription.setPrescribedAt(now);
        prescription.setCreatedAt(now);
        prescription = prescriptionRepository.save(prescription);

        replaceItems(prescription.getId(), request.items());
        return toResponse(prescription);
    }

    @Transactional
    public PrescriptionResponse update(UUID prescriptionId, PrescriptionRequest request) {
        Prescription prescription = findPrescriptionOrThrow(prescriptionId);
        clinicalDirectory.requireModifiableEncounter(prescription.getEncounterId());
        PrescriptionRules.checkEditable(prescription.getStatus(), prescription.getVoidedAt() != null);
        requireDiagnosisOfEncounter(request.encounterDiagnosisId(), prescription.getEncounterId());

        prescription.setEncounterDiagnosisId(request.encounterDiagnosisId());
        prescription.setGeneralInstructions(request.generalInstructions());
        prescription = prescriptionRepository.save(prescription);

        replaceItems(prescriptionId, request.items());
        return toResponse(prescription);
    }

    @Transactional
    public PrescriptionResponse issue(UUID prescriptionId) {
        Prescription prescription = findPrescriptionOrThrow(prescriptionId);
        clinicalDirectory.requireModifiableEncounter(prescription.getEncounterId());
        long itemCount = prescriptionItemRepository.countByPrescriptionId(prescriptionId);
        PrescriptionRules.checkCanIssue(prescription.getStatus(), prescription.getVoidedAt() != null, itemCount);

        prescription.setStatus(PrescriptionStatus.ISSUED);
        prescription.setPrescribedAt(clinicClock.now());
        return toResponse(prescriptionRepository.save(prescription));
    }

    @Transactional
    public PrescriptionResponse voidPrescription(UUID prescriptionId) {
        Prescription prescription = findPrescriptionOrThrow(prescriptionId);
        clinicalDirectory.requireModifiableEncounter(prescription.getEncounterId());
        PrescriptionRules.checkCanVoid(prescription.getVoidedAt() != null);

        prescription.setVoidedAt(clinicClock.now());
        return toResponse(prescriptionRepository.save(prescription));
    }

    @Transactional(readOnly = true)
    public PrescriptionResponse findById(UUID prescriptionId) {
        return toResponse(findPrescriptionOrThrow(prescriptionId));
    }

    @Transactional(readOnly = true)
    public List<PrescriptionResponse> findEncounterPrescriptions(UUID encounterId) {
        clinicalDirectory.findEncounter(encounterId)
                .orElseThrow(() -> new ResourceNotFoundException("Atención no encontrada"));

        return prescriptionRepository.findByEncounterIdAndVoidedAtIsNullOrderByCreatedAtAsc(encounterId).stream()
                .map(this::toResponse)
                .toList();
    }

    private Prescription findPrescriptionOrThrow(UUID prescriptionId) {
        return prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Receta no encontrada"));
    }

    private void requireDiagnosisOfEncounter(UUID encounterDiagnosisId, UUID encounterId) {
        DiagnosisSummary diagnosis = clinicalDirectory.findDiagnosis(encounterDiagnosisId)
                .orElseThrow(() -> new InvalidInputException("Diagnóstico no existe"));

        if (!diagnosis.encounterId().equals(encounterId)) {
            throw new BusinessException("El diagnóstico no pertenece a esta atención.");
        }
        if (diagnosis.voided()) {
            throw new BusinessException("El diagnóstico está anulado.");
        }
    }

    /** Reemplaza los ítems de la receta. Solo se llama con la receta en DRAFT (ver checkEditable). */
    private void replaceItems(UUID prescriptionId, List<PrescriptionItemRequest> requests) {
        List<PrescriptionItem> items = requests.stream()
                .map(request -> toItem(prescriptionId, request))
                .toList();
        prescriptionItemRepository.deleteByPrescriptionId(prescriptionId);
        prescriptionItemRepository.saveAll(items);
    }

    private PrescriptionItem toItem(UUID prescriptionId, PrescriptionItemRequest request) {
        Medication medication = medicationRepository.findById(request.medicationId())
                .filter(Medication::isActive)
                .orElseThrow(() -> new InvalidInputException("Medicamento inactivo o no existe"));
        MedicationPreset preset = request.medicationPresetId() != null
                ? requirePresetOf(request.medicationPresetId(), medication.getId())
                : null;

        String dose = PrescriptionRules.resolveItemField(request.dose(), preset != null ? preset.getDose() : null);
        String frequency = PrescriptionRules.resolveItemField(request.frequency(), preset != null ? preset.getFrequency() : null);
        String duration = PrescriptionRules.resolveItemField(request.duration(), preset != null ? preset.getDuration() : null);
        if (isBlank(dose) || isBlank(frequency) || isBlank(duration)) {
            throw new InvalidInputException("La dosis, la frecuencia y la duración son obligatorias si no se elige un preset");
        }

        PrescriptionItem item = new PrescriptionItem();
        item.setPrescriptionId(prescriptionId);
        item.setMedicationId(medication.getId());
        item.setMedicationPresetId(preset != null ? preset.getId() : null);
        item.setDose(dose);
        item.setFrequency(frequency);
        item.setDuration(duration);
        item.setQuantity(request.quantity());
        item.setInstructions(PrescriptionRules.resolveItemField(request.instructions(), preset != null ? preset.getInstructions() : null));
        return item;
    }

    private MedicationPreset requirePresetOf(Long medicationPresetId, Long medicationId) {
        MedicationPreset preset = medicationPresetRepository.findById(medicationPresetId)
                .orElseThrow(() -> new InvalidInputException("Preset no existe"));
        if (!preset.getMedicationId().equals(medicationId)) {
            throw new InvalidInputException("El preset no corresponde al medicamento");
        }
        if (!preset.isActive()) {
            throw new BusinessException("La posología está desactivada.");
        }
        return preset;
    }

    private PrescriptionResponse toResponse(Prescription prescription) {
        List<PrescriptionItem> items = prescriptionItemRepository.findByPrescriptionId(prescription.getId());
        Map<Long, Medication> medicationsById = medicationRepository.findAllById(
                        items.stream().map(PrescriptionItem::getMedicationId).distinct().toList()).stream()
                .collect(Collectors.toMap(Medication::getId, Function.identity()));
        DiagnosisSummary diagnosis = prescription.getEncounterDiagnosisId() != null
                ? clinicalDirectory.findDiagnosis(prescription.getEncounterDiagnosisId()).orElse(null)
                : null;

        return new PrescriptionResponse(
                prescription.getId(),
                prescription.getEncounterId(),
                prescription.getEncounterDiagnosisId(),
                diagnosis != null ? diagnosis.icd10Code() : null,
                diagnosis != null ? diagnosis.icd10Description() : null,
                prescription.getStatus(),
                prescription.getPrescribedAt(),
                prescription.getGeneralInstructions(),
                prescription.getVoidedAt() != null,
                items.stream().map(item -> toItemResponse(item, medicationsById.get(item.getMedicationId()))).toList()
        );
    }

    private PrescriptionItemResponse toItemResponse(PrescriptionItem item, Medication medication) {
        return new PrescriptionItemResponse(
                item.getId(),
                item.getMedicationId(),
                medication.getName(),
                medication.getPresentation(),
                medication.getConcentration(),
                item.getMedicationPresetId(),
                item.getDose(),
                item.getFrequency(),
                item.getDuration(),
                item.getQuantity(),
                item.getInstructions()
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
