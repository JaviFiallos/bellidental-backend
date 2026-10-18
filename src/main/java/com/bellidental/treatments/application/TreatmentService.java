package com.bellidental.treatments.application;

import com.bellidental.clinical.ClinicalDirectory;
import com.bellidental.clinical.DiagnosisSummary;
import com.bellidental.clinical.EncounterSummary;
import com.bellidental.persistence.entity.PerformedTreatment;
import com.bellidental.persistence.entity.Treatment;
import com.bellidental.persistence.repository.PerformedTreatmentRepository;
import com.bellidental.persistence.repository.ToothRepository;
import com.bellidental.persistence.repository.ToothSurfaceRepository;
import com.bellidental.persistence.repository.TreatmentRepository;
import com.bellidental.shared.exception.BusinessException;
import com.bellidental.shared.exception.InvalidInputException;
import com.bellidental.shared.exception.ResourceNotFoundException;
import com.bellidental.shared.time.ClinicClock;
import com.bellidental.treatments.api.dto.PerformedTreatmentResponse;
import com.bellidental.treatments.api.dto.RecordTreatmentRequest;
import com.bellidental.treatments.api.dto.TreatmentResponse;
import com.bellidental.treatments.api.dto.VoidTreatmentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TreatmentService {

    private static final int MIN_QUERY_LENGTH = 2;
    private static final int DEFAULT_QUANTITY = 1;

    private final TreatmentRepository treatmentRepository;
    private final PerformedTreatmentRepository performedTreatmentRepository;
    private final ToothRepository toothRepository;
    private final ToothSurfaceRepository toothSurfaceRepository;
    private final ClinicalDirectory clinicalDirectory;
    private final ClinicClock clinicClock;

    @Transactional(readOnly = true)
    public List<TreatmentResponse> searchCatalog(String query) {
        List<Treatment> treatments = (query == null || query.isBlank())
                ? treatmentRepository.findByActiveTrueOrderByCodeAsc()
                : treatmentRepository.searchActiveByCodeOrName(requireValidQuery(query));
        return treatments.stream().map(this::toTreatmentResponse).toList();
    }

    @Transactional
    public PerformedTreatmentResponse recordTreatment(UUID encounterId, RecordTreatmentRequest request) {
        EncounterSummary encounter = clinicalDirectory.requireModifiableEncounter(encounterId);
        Treatment treatment = treatmentRepository.findById(request.treatmentId())
                .filter(Treatment::isActive)
                .orElseThrow(() -> new InvalidInputException("Tratamiento inactivo o no existe"));

        if (request.encounterDiagnosisId() != null) {
            requireDiagnosisOfPatient(request.encounterDiagnosisId(), encounter.patientId());
        }
        requireExistingTooth(request.toothCode());
        requireExistingSurface(request.surfaceCode());

        PerformedTreatment performed = new PerformedTreatment();
        performed.setEncounterId(encounterId);
        performed.setTreatmentId(treatment.getId());
        performed.setEncounterDiagnosisId(request.encounterDiagnosisId());
        performed.setToothCode(request.toothCode());
        performed.setSurfaceCode(request.surfaceCode());
        performed.setQuantity(request.quantity() != null ? request.quantity() : DEFAULT_QUANTITY);
        performed.setPrice(request.price() != null ? request.price() : treatment.getBasePrice());
        performed.setNotes(request.notes());
        performed.setCreatedAt(clinicClock.now());

        performed = performedTreatmentRepository.save(performed);
        return toPerformedResponse(performed, treatment);
    }

    @Transactional(readOnly = true)
    public List<PerformedTreatmentResponse> getEncounterTreatments(UUID encounterId) {
        clinicalDirectory.findEncounter(encounterId)
                .orElseThrow(() -> new ResourceNotFoundException("Atención no encontrada"));

        List<PerformedTreatment> performed = performedTreatmentRepository.findByEncounterIdAndVoidedAtIsNullOrderByCreatedAtAsc(encounterId);
        List<Long> treatmentIds = performed.stream().map(PerformedTreatment::getTreatmentId).distinct().toList();
        Map<Long, Treatment> treatmentsById = treatmentRepository.findAllById(treatmentIds).stream()
                .collect(Collectors.toMap(Treatment::getId, Function.identity()));

        return performed.stream()
                .map(p -> toPerformedResponse(p, treatmentsById.get(p.getTreatmentId())))
                .toList();
    }

    @Transactional
    public void voidTreatment(UUID performedTreatmentId, VoidTreatmentRequest request) {
        PerformedTreatment performed = performedTreatmentRepository.findById(performedTreatmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tratamiento realizado no encontrado"));

        if (performed.getVoidedAt() != null) {
            throw new BusinessException("El tratamiento ya está anulado");
        }

        clinicalDirectory.requireModifiableEncounter(performed.getEncounterId());

        performed.setVoidedAt(clinicClock.now());
        performed.setVoidReason(request.voidReason());
        performedTreatmentRepository.save(performed);
    }

    private String requireValidQuery(String query) {
        String term = query.trim();
        if (term.length() < MIN_QUERY_LENGTH) {
            throw new InvalidInputException("La búsqueda requiere al menos " + MIN_QUERY_LENGTH + " caracteres");
        }
        return term;
    }

    private void requireDiagnosisOfPatient(UUID encounterDiagnosisId, UUID patientId) {
        DiagnosisSummary diagnosis = clinicalDirectory.findDiagnosis(encounterDiagnosisId)
                .orElseThrow(() -> new InvalidInputException("Diagnóstico no existe"));

        if (diagnosis.voided()) {
            throw new BusinessException("El diagnóstico está anulado.");
        }

        UUID diagnosisPatientId = clinicalDirectory.findEncounter(diagnosis.encounterId())
                .map(EncounterSummary::patientId)
                .orElse(null);
        if (!patientId.equals(diagnosisPatientId)) {
            throw new BusinessException("El diagnóstico no pertenece a este paciente.");
        }
    }

    private void requireExistingTooth(Short toothCode) {
        if (toothCode != null && !toothRepository.existsById(toothCode)) {
            throw new InvalidInputException("Pieza no existe");
        }
    }

    private void requireExistingSurface(String surfaceCode) {
        if (surfaceCode != null && !toothSurfaceRepository.existsById(surfaceCode)) {
            throw new InvalidInputException("Cara no existe");
        }
    }

    private TreatmentResponse toTreatmentResponse(Treatment treatment) {
        return new TreatmentResponse(
                treatment.getId(),
                treatment.getCode(),
                treatment.getName(),
                treatment.getDescription(),
                treatment.getBasePrice()
        );
    }

    private PerformedTreatmentResponse toPerformedResponse(PerformedTreatment performed, Treatment treatment) {
        BigDecimal total = performed.getPrice().multiply(BigDecimal.valueOf(performed.getQuantity()));
        return new PerformedTreatmentResponse(
                performed.getId(),
                performed.getEncounterId(),
                performed.getTreatmentId(),
                treatment.getCode(),
                treatment.getName(),
                performed.getEncounterDiagnosisId(),
                performed.getToothCode(),
                performed.getSurfaceCode(),
                performed.getQuantity(),
                performed.getPrice(),
                total,
                performed.getNotes(),
                performed.getCreatedAt()
        );
    }
}
