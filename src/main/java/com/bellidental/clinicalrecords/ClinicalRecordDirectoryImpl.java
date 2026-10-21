package com.bellidental.clinicalrecords;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ClinicalRecordDirectoryImpl implements ClinicalRecordDirectory {

    private final ClinicalRecordService clinicalRecordService;

    @Override
    public Optional<ClinicalRecordSummary> findByPatientId(UUID patientId) {
        return clinicalRecordService.findSummaryByPatientId(patientId);
    }

    @Override
    public ClinicalRecordSummary openIfAbsent(UUID patientId, UUID professionalId) {
        return clinicalRecordService.openIfAbsent(patientId, professionalId);
    }
}
