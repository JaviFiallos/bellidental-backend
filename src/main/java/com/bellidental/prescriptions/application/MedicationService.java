package com.bellidental.prescriptions.application;

import com.bellidental.persistence.entity.Medication;
import com.bellidental.persistence.entity.MedicationPreset;
import com.bellidental.persistence.repository.MedicationPresetRepository;
import com.bellidental.persistence.repository.MedicationRepository;
import com.bellidental.prescriptions.api.dto.MedicationPresetResponse;
import com.bellidental.prescriptions.api.dto.MedicationResponse;
import com.bellidental.shared.exception.InvalidInputException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MedicationService {

    private final MedicationRepository medicationRepository;
    private final MedicationPresetRepository medicationPresetRepository;

    @Transactional(readOnly = true)
    public List<MedicationResponse> searchMedications(String query) {
        List<Medication> medications = medicationRepository.searchActive(requireQuery(query));
        List<Long> medicationIds = medications.stream().map(Medication::getId).toList();
        Map<Long, List<MedicationPreset>> presetsByMedication = medicationPresetRepository
                .findByMedicationIdInOrderByIdAsc(medicationIds).stream()
                .collect(Collectors.groupingBy(MedicationPreset::getMedicationId));

        return medications.stream()
                .map(medication -> toResponse(medication, presetsByMedication.getOrDefault(medication.getId(), List.of())))
                .toList();
    }

    private String requireQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new InvalidInputException("La búsqueda requiere al menos 1 carácter");
        }
        return query.trim();
    }

    private MedicationResponse toResponse(Medication medication, List<MedicationPreset> presets) {
        return new MedicationResponse(
                medication.getId(),
                medication.getName(),
                medication.getPresentation(),
                medication.getConcentration(),
                medication.getRoute(),
                presets.stream().map(this::toPresetResponse).toList()
        );
    }

    private MedicationPresetResponse toPresetResponse(MedicationPreset preset) {
        return new MedicationPresetResponse(
                preset.getId(),
                preset.getLabel(),
                preset.getDose(),
                preset.getFrequency(),
                preset.getDuration(),
                preset.getInstructions()
        );
    }
}
