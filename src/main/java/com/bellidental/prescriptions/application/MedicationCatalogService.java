package com.bellidental.prescriptions.application;

import com.bellidental.persistence.entity.Medication;
import com.bellidental.persistence.entity.MedicationPreset;
import com.bellidental.persistence.repository.MedicationPresetRepository;
import com.bellidental.persistence.repository.MedicationRepository;
import com.bellidental.persistence.repository.PrescriptionItemRepository;
import com.bellidental.prescriptions.api.dto.MedicationPresetRequest;
import com.bellidental.prescriptions.api.dto.MedicationPresetResponse;
import com.bellidental.prescriptions.api.dto.MedicationRequest;
import com.bellidental.prescriptions.api.dto.MedicationResponse;
import com.bellidental.shared.exception.BusinessException;
import com.bellidental.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Alta, edición y activación del catálogo compartido de medicamentos y sus posologías. */
@Service
@RequiredArgsConstructor
public class MedicationCatalogService {

    private final MedicationRepository medicationRepository;
    private final MedicationPresetRepository medicationPresetRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final MedicationService medicationService;

    @Transactional(readOnly = true)
    public List<MedicationResponse> listAll() {
        List<Medication> medications = medicationRepository.findAllByOrderByNameAscConcentrationAsc();
        List<Long> medicationIds = medications.stream().map(Medication::getId).toList();
        Map<Long, List<MedicationPreset>> presetsByMedication = medicationPresetRepository
                .findByMedicationIdInOrderByIdAsc(medicationIds).stream()
                .collect(Collectors.groupingBy(MedicationPreset::getMedicationId));

        return medications.stream()
                .map(medication -> medicationService.toResponse(medication,
                        presetsByMedication.getOrDefault(medication.getId(), List.of())))
                .toList();
    }

    @Transactional
    public MedicationResponse create(MedicationRequest request) {
        requireUniquePresentation(request, null);
        Medication medication = new Medication();
        medication.setActive(true);
        applyFields(medication, request);
        return toResponse(medicationRepository.save(medication));
    }

    /** Un medicamento ya usado en una receta no se edita: sus datos aparecen en recetas emitidas. */
    @Transactional
    public MedicationResponse update(Long medicationId, MedicationRequest request) {
        Medication medication = findMedication(medicationId);
        if (prescriptionItemRepository.existsByMedicationId(medicationId)) {
            throw new BusinessException("El medicamento ya se usó en recetas: desactívalo y crea uno nuevo.");
        }
        requireUniquePresentation(request, medicationId);
        applyFields(medication, request);
        return toResponse(medicationRepository.save(medication));
    }

    @Transactional
    public MedicationResponse setActive(Long medicationId, boolean active) {
        Medication medication = findMedication(medicationId);
        medication.setActive(active);
        return toResponse(medicationRepository.save(medication));
    }

    @Transactional
    public MedicationPresetResponse createPreset(Long medicationId, MedicationPresetRequest request) {
        Medication medication = findMedication(medicationId);
        if (!medication.isActive()) {
            throw new BusinessException("El medicamento está desactivado.");
        }
        requireUniqueLabel(medicationId, request.label(), null);
        MedicationPreset preset = new MedicationPreset();
        preset.setMedicationId(medicationId);
        preset.setActive(true);
        applyFields(preset, request);
        return medicationService.toPresetResponse(medicationPresetRepository.save(preset));
    }

    @Transactional
    public MedicationPresetResponse updatePreset(Long presetId, MedicationPresetRequest request) {
        MedicationPreset preset = findPreset(presetId);
        requireUniqueLabel(preset.getMedicationId(), request.label(), presetId);
        applyFields(preset, request);
        return medicationService.toPresetResponse(medicationPresetRepository.save(preset));
    }

    @Transactional
    public MedicationPresetResponse setPresetActive(Long presetId, boolean active) {
        MedicationPreset preset = findPreset(presetId);
        preset.setActive(active);
        return medicationService.toPresetResponse(medicationPresetRepository.save(preset));
    }

    private Medication findMedication(Long medicationId) {
        return medicationRepository.findById(medicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Medicamento", medicationId));
    }

    private MedicationPreset findPreset(Long presetId) {
        return medicationPresetRepository.findById(presetId)
                .orElseThrow(() -> new ResourceNotFoundException("Posología", presetId));
    }

    private void requireUniquePresentation(MedicationRequest request, Long medicationId) {
        String name = request.name().trim();
        String presentation = request.presentation().trim();
        String concentration = request.concentration().trim();
        boolean exists = medicationId == null
                ? medicationRepository.existsByNameIgnoreCaseAndConcentrationIgnoreCaseAndPresentationIgnoreCase(name, concentration, presentation)
                : medicationRepository.existsByNameIgnoreCaseAndConcentrationIgnoreCaseAndPresentationIgnoreCaseAndIdNot(name, concentration, presentation, medicationId);
        if (exists) {
            throw new BusinessException("Ya existe ese medicamento con esa presentación y concentración.");
        }
    }

    private void requireUniqueLabel(Long medicationId, String label, Long presetId) {
        String trimmedLabel = label.trim();
        boolean exists = presetId == null
                ? medicationPresetRepository.existsByMedicationIdAndLabelIgnoreCase(medicationId, trimmedLabel)
                : medicationPresetRepository.existsByMedicationIdAndLabelIgnoreCaseAndIdNot(medicationId, trimmedLabel, presetId);
        if (exists) {
            throw new BusinessException("Ese medicamento ya tiene una posología con esa etiqueta.");
        }
    }

    private void applyFields(Medication medication, MedicationRequest request) {
        medication.setName(request.name().trim());
        medication.setPresentation(request.presentation().trim());
        medication.setConcentration(request.concentration().trim());
        medication.setRoute(blankToNull(request.route()));
    }

    private void applyFields(MedicationPreset preset, MedicationPresetRequest request) {
        preset.setLabel(request.label().trim());
        preset.setDose(request.dose().trim());
        preset.setFrequency(request.frequency().trim());
        preset.setDuration(request.duration().trim());
        preset.setInstructions(blankToNull(request.instructions()));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private MedicationResponse toResponse(Medication medication) {
        List<MedicationPreset> presets = medicationPresetRepository.findByMedicationIdInOrderByIdAsc(List.of(medication.getId()));
        return medicationService.toResponse(medication, presets);
    }
}
