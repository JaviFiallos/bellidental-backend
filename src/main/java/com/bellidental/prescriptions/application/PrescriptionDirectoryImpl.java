package com.bellidental.prescriptions.application;

import com.bellidental.persistence.entity.Medication;
import com.bellidental.persistence.entity.Prescription;
import com.bellidental.persistence.entity.PrescriptionItem;
import com.bellidental.persistence.repository.MedicationRepository;
import com.bellidental.persistence.repository.PrescriptionItemRepository;
import com.bellidental.persistence.repository.PrescriptionRepository;
import com.bellidental.prescriptions.PrescriptionDirectory;
import com.bellidental.prescriptions.PrescriptionItemSummary;
import com.bellidental.prescriptions.PrescriptionSummary;
import com.bellidental.shared.enums.PrescriptionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PrescriptionDirectoryImpl implements PrescriptionDirectory {

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final MedicationRepository medicationRepository;

    @Override
    public List<PrescriptionSummary> findIssued(Collection<UUID> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Prescription> prescriptions = prescriptionRepository
                .findByEncounterIdInAndStatusAndVoidedAtIsNullOrderByPrescribedAtAsc(
                        encounterIds,
                        PrescriptionStatus.ISSUED
                );
        if (prescriptions.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> prescriptionIds = prescriptions.stream()
                .map(Prescription::getId)
                .toList();

        List<PrescriptionItem> items = prescriptionItemRepository.findByPrescriptionIdIn(prescriptionIds);

        Set<Long> medicationIds = items.stream()
                .map(PrescriptionItem::getMedicationId)
                .collect(Collectors.toSet());

        Map<Long, Medication> medicationMap = medicationRepository.findAllById(medicationIds).stream()
                .collect(Collectors.toMap(Medication::getId, Function.identity()));

        Map<UUID, List<PrescriptionItemSummary>> itemsByPrescriptionId = items.stream()
                .collect(Collectors.groupingBy(
                        PrescriptionItem::getPrescriptionId,
                        Collectors.mapping(item -> {
                            Medication med = medicationMap.get(item.getMedicationId());
                            return new PrescriptionItemSummary(
                                    med != null ? med.getName() : null,
                                    med != null ? med.getConcentration() : null,
                                    med != null ? med.getPresentation() : null,
                                    item.getDose(),
                                    item.getFrequency(),
                                    item.getDuration(),
                                    item.getQuantity()
                            );
                        }, Collectors.toList())
                ));

        return prescriptions.stream()
                .map(p -> new PrescriptionSummary(
                        p.getId(),
                        p.getEncounterId(),
                        p.getPrescribedAt(),
                        p.getGeneralInstructions(),
                        itemsByPrescriptionId.getOrDefault(p.getId(), Collections.emptyList())
                ))
                .toList();
    }
}
