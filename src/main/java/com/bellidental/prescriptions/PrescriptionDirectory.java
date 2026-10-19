package com.bellidental.prescriptions;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PrescriptionDirectory {
    List<PrescriptionSummary> findIssued(Collection<UUID> encounterIds);
}
