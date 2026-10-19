package com.bellidental.treatments.application;

import com.bellidental.persistence.entity.Treatment;
import com.bellidental.persistence.repository.TreatmentRepository;
import com.bellidental.treatments.TreatmentDirectory;
import com.bellidental.treatments.TreatmentPriceSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TreatmentDirectoryImpl implements TreatmentDirectory {

    private final TreatmentRepository treatmentRepository;

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
}
