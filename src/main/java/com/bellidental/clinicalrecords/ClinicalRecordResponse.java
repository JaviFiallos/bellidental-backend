package com.bellidental.clinicalrecords;

import com.bellidental.persistence.entity.ClinicalRecord;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ClinicalRecordResponse(
        UUID id,
        UUID patientId,
        String recordNumber,
        LocalDate openedAt,
        UUID openedBy,
        boolean noKnownHistory,
        String currentMedication,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<MedicalHistoryEntryResponse> historyEntries
) {
    public static ClinicalRecordResponse from(ClinicalRecord record, List<MedicalHistoryEntryResponse> historyEntries) {
        return new ClinicalRecordResponse(
                record.getId(),
                record.getPatient().getId(),
                record.getRecordNumber(),
                record.getOpenedAt(),
                record.getOpenedBy().getId(),
                record.isNoKnownHistory(),
                record.getCurrentMedication(),
                record.getCreatedAt(),
                record.getUpdatedAt(),
                historyEntries
        );
    }
}
