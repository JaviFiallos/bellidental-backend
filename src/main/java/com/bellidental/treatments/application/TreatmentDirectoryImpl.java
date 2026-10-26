package com.bellidental.treatments.application;

import com.bellidental.persistence.entity.PerformedTreatment;
import com.bellidental.persistence.entity.Treatment;
import com.bellidental.persistence.repository.PerformedTreatmentRepository;
import com.bellidental.persistence.repository.TreatmentRepository;
import com.bellidental.treatments.PerformedTreatmentSummary;
import com.bellidental.treatments.TreatmentDirectory;
import com.bellidental.treatments.TreatmentPriceSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TreatmentDirectoryImpl implements TreatmentDirectory {

    private final TreatmentRepository treatmentRepository;
    private final PerformedTreatmentRepository performedTreatmentRepository;

    @Override
    public List<TreatmentPriceSummary> findActiveByCodes(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Collections.emptyList();
        }
        List<Treatment> treatments = treatmentRepository.findByCodeInAndActiveTrue(codes);
        Map<String, TreatmentPriceSummary> byCode = treatments.stream()
                .collect(Collectors.toMap(
                        Treatment::getCode,
                        t -> new TreatmentPriceSummary(t.getCode(), t.getName(), t.getBasePrice())
                ));
        return codes.stream()
                .map(byCode::get)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public List<PerformedTreatmentSummary> findPerformed(Collection<UUID> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<PerformedTreatment> performed = performedTreatmentRepository
                .findByEncounterIdInAndVoidedAtIsNullOrderByCreatedAtAsc(encounterIds);
        if (performed.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> treatmentIds = performed.stream()
                .map(PerformedTreatment::getTreatmentId)
                .collect(Collectors.toSet());
        Map<Long, Treatment> treatmentMap = treatmentRepository.findAllById(treatmentIds).stream()
                .collect(Collectors.toMap(Treatment::getId, Function.identity()));

        return performed.stream()
                .map(pt -> {
                    Treatment t = treatmentMap.get(pt.getTreatmentId());
                    return new PerformedTreatmentSummary(
                            pt.getId(),
                            pt.getEncounterId(),
                            t != null ? t.getCode() : null,
                            t != null ? t.getName() : null,
                            pt.getToothCode(),
                            pt.getSurfaceCode(),
                            pt.getQuantity(),
                            pt.getPrice()
                    );
                })
                .toList();
    }
}
