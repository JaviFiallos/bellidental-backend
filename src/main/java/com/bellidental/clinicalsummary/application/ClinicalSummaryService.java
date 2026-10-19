package com.bellidental.clinicalsummary.application;

import com.bellidental.clinical.ClinicalDirectory;
import com.bellidental.clinical.DiagnosisSummary;
import com.bellidental.clinical.EncounterSummary;
import com.bellidental.clinicalrecords.ClinicalRecordDirectory;
import com.bellidental.clinicalrecords.ClinicalRecordSummary;
import com.bellidental.clinicalsummary.api.dto.ClinicalSummaryResponse;
import com.bellidental.clinicalsummary.api.dto.ClinicalSummaryResponse.DiagnosisItem;
import com.bellidental.clinicalsummary.api.dto.ClinicalSummaryResponse.PrescriptionItem;
import com.bellidental.clinicalsummary.api.dto.ClinicalSummaryResponse.PrescriptionMedicationItem;
import com.bellidental.clinicalsummary.api.dto.ClinicalSummaryResponse.Session;
import com.bellidental.clinicalsummary.api.dto.ClinicalSummaryResponse.SessionProfessional;
import com.bellidental.clinicalsummary.api.dto.ClinicalSummaryResponse.TreatmentItem;
import com.bellidental.config.ClinicProperties;
import com.bellidental.patients.PatientDirectory;
import com.bellidental.prescriptions.PrescriptionDirectory;
import com.bellidental.prescriptions.PrescriptionItemSummary;
import com.bellidental.prescriptions.PrescriptionSummary;
import com.bellidental.professionals.ProfessionalDirectory;
import com.bellidental.professionals.ProfessionalSummary;
import com.bellidental.shared.exception.ResourceNotFoundException;
import com.bellidental.treatments.PerformedTreatmentSummary;
import com.bellidental.treatments.TreatmentDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ClinicalSummaryService {

    private final PatientDirectory patientDirectory;
    private final ClinicalRecordDirectory clinicalRecordDirectory;
    private final ClinicalDirectory clinicalDirectory;
    private final TreatmentDirectory treatmentDirectory;
    private final PrescriptionDirectory prescriptionDirectory;
    private final ProfessionalDirectory professionalDirectory;
    private final ClinicProperties clinicProperties;

    public ClinicalSummaryResponse getSummary(UUID patientId) {
        patientDirectory.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente", patientId));

        Optional<ClinicalRecordSummary> clinicalRecord = clinicalRecordDirectory.findByPatientId(patientId);
        List<EncounterSummary> encounters = clinicalDirectory.findPatientEncounters(patientId);

        List<UUID> encounterIds = encounters.stream()
                .map(EncounterSummary::id)
                .toList();

        Map<UUID, List<DiagnosisItem>> diagnosesByEncounter = findDiagnosesByEncounter(encounterIds);
        Map<UUID, List<TreatmentItem>> treatmentsByEncounter = findTreatmentsByEncounter(encounterIds);
        Map<UUID, List<PrescriptionItem>> prescriptionsByEncounter = findPrescriptionsByEncounter(encounterIds);
        Map<UUID, ProfessionalSummary> professionalsById = findProfessionalsById(clinicalRecord, encounters);

        List<Session> sessions = buildSessions(
                encounters,
                professionalsById,
                diagnosesByEncounter,
                treatmentsByEncounter,
                prescriptionsByEncounter
        );

        String clinicName = clinicProperties.name();
        String recordNumber = clinicalRecord.map(ClinicalRecordSummary::recordNumber).orElse(null);
        LocalDate openedAt = clinicalRecord.map(ClinicalRecordSummary::openedAt).orElse(null);
        SessionProfessional openedBy = clinicalRecord
                .map(ClinicalRecordSummary::openedByProfessionalId)
                .map(id -> toSessionProfessional(id, professionalsById))
                .orElse(null);

        return new ClinicalSummaryResponse(
                patientId,
                clinicName,
                recordNumber,
                openedAt,
                openedBy,
                sessions
        );
    }

    private Map<UUID, List<DiagnosisItem>> findDiagnosesByEncounter(List<UUID> encounterIds) {
        if (encounterIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<DiagnosisSummary> diagnoses = clinicalDirectory.findDiagnoses(encounterIds);
        return diagnoses.stream()
                .collect(Collectors.groupingBy(
                        DiagnosisSummary::encounterId,
                        Collectors.mapping(
                                diagnosis -> new DiagnosisItem(
                                        diagnosis.id(),
                                        diagnosis.icd10Code(),
                                        diagnosis.icd10Description(),
                                        diagnosis.certainty(),
                                        diagnosis.notes()
                                ),
                                Collectors.toList()
                        )
                ));
    }

    private Map<UUID, List<TreatmentItem>> findTreatmentsByEncounter(List<UUID> encounterIds) {
        if (encounterIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<PerformedTreatmentSummary> treatments = treatmentDirectory.findPerformed(encounterIds);
        return treatments.stream()
                .collect(Collectors.groupingBy(
                        PerformedTreatmentSummary::encounterId,
                        Collectors.mapping(
                                treatment -> new TreatmentItem(
                                        treatment.id(),
                                        treatment.treatmentCode(),
                                        treatment.treatmentName(),
                                        treatment.toothCode(),
                                        treatment.surfaceCode(),
                                        treatment.quantity()
                                ),
                                Collectors.toList()
                        )
                ));
    }

    private Map<UUID, List<PrescriptionItem>> findPrescriptionsByEncounter(List<UUID> encounterIds) {
        if (encounterIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<PrescriptionSummary> prescriptions = prescriptionDirectory.findIssued(encounterIds);
        return prescriptions.stream()
                .collect(Collectors.groupingBy(
                        PrescriptionSummary::encounterId,
                        Collectors.mapping(
                                prescription -> new PrescriptionItem(
                                        prescription.id(),
                                        prescription.prescribedAt(),
                                        prescription.generalInstructions(),
                                        toMedicationItems(prescription.items())
                                ),
                                Collectors.toList()
                        )
                ));
    }

    private List<PrescriptionMedicationItem> toMedicationItems(List<PrescriptionItemSummary> items) {
        return items.stream()
                .map(item -> new PrescriptionMedicationItem(
                        item.medicationName(),
                        item.concentration(),
                        item.presentation(),
                        item.dose(),
                        item.frequency(),
                        item.duration(),
                        item.quantity()
                ))
                .toList();
    }

    private Map<UUID, ProfessionalSummary> findProfessionalsById(
            Optional<ClinicalRecordSummary> clinicalRecord,
            List<EncounterSummary> encounters
    ) {
        Set<UUID> professionalIds = new HashSet<>();
        clinicalRecord.map(ClinicalRecordSummary::openedByProfessionalId).ifPresent(professionalIds::add);
        encounters.stream().map(EncounterSummary::professionalId).forEach(professionalIds::add);

        if (professionalIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return professionalDirectory.findByIds(professionalIds).stream()
                .collect(Collectors.toMap(ProfessionalSummary::id, Function.identity(), (a, b) -> a));
    }

    private List<Session> buildSessions(
            List<EncounterSummary> encounters,
            Map<UUID, ProfessionalSummary> professionalsById,
            Map<UUID, List<DiagnosisItem>> diagnosesByEncounter,
            Map<UUID, List<TreatmentItem>> treatmentsByEncounter,
            Map<UUID, List<PrescriptionItem>> prescriptionsByEncounter
    ) {
        List<Session> sessions = new ArrayList<>();
        for (int index = 0; index < encounters.size(); index++) {
            EncounterSummary encounter = encounters.get(index);
            int sessionNumber = index + 1;
            sessions.add(toSession(
                    encounter,
                    sessionNumber,
                    professionalsById,
                    diagnosesByEncounter,
                    treatmentsByEncounter,
                    prescriptionsByEncounter
            ));
        }
        return sessions;
    }

    private Session toSession(
            EncounterSummary encounter,
            int sessionNumber,
            Map<UUID, ProfessionalSummary> professionalsById,
            Map<UUID, List<DiagnosisItem>> diagnosesByEncounter,
            Map<UUID, List<TreatmentItem>> treatmentsByEncounter,
            Map<UUID, List<PrescriptionItem>> prescriptionsByEncounter
    ) {
        return new Session(
                encounter.id(),
                sessionNumber,
                encounter.startAt(),
                encounter.status(),
                toSessionProfessional(encounter.professionalId(), professionalsById),
                diagnosesByEncounter.getOrDefault(encounter.id(), Collections.emptyList()),
                treatmentsByEncounter.getOrDefault(encounter.id(), Collections.emptyList()),
                prescriptionsByEncounter.getOrDefault(encounter.id(), Collections.emptyList())
        );
    }

    private SessionProfessional toSessionProfessional(
            UUID professionalId,
            Map<UUID, ProfessionalSummary> professionalsById
    ) {
        if (professionalId == null) {
            return null;
        }
        ProfessionalSummary professional = professionalsById.get(professionalId);
        if (professional == null) {
            return null;
        }
        String fullName = (professional.firstName() + " " + professional.lastName()).trim();
        return new SessionProfessional(professional.id(), fullName, professional.licenseNumber());
    }
}
