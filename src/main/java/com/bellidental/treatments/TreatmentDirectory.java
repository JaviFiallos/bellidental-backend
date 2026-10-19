package com.bellidental.treatments;

import java.util.Collection;
import java.util.List;

public interface TreatmentDirectory {
    List<TreatmentPriceSummary> findActiveByCodes(Collection<String> codes);
}
