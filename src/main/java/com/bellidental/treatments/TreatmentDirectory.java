package com.bellidental.treatments;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TreatmentDirectory {
    List<TreatmentPriceSummary> findActiveByCodes(Collection<String> codes);

    List<PerformedTreatmentSummary> findPerformed(Collection<UUID> encounterIds);
}
