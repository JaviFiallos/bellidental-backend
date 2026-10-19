package com.bellidental.clinical.application;

import com.bellidental.appointments.AppointmentScheduler;
import com.bellidental.appointments.AppointmentSummary;
import com.bellidental.clinical.EncounterSummary;
import com.bellidental.clinical.api.dto.AddDiagnosisRequest;
import com.bellidental.clinical.api.dto.DiagnosisResponse;
import com.bellidental.clinical.api.dto.EncounterResponse;
import com.bellidental.clinical.api.dto.OpenEncounterRequest;
import com.bellidental.clinical.api.dto.UpdateEncounterRequest;
import com.bellidental.clinical.api.dto.VoidEncounterRequest;
import com.bellidental.clinical.domain.EncounterPolicy;
import com.bellidental.clinicalrecords.ClinicalRecordDirectory;
import com.bellidental.persistence.entity.Encounter;
import com.bellidental.persistence.entity.EncounterDiagnosis;
import com.bellidental.persistence.entity.Icd10Code;
import com.bellidental.persistence.repository.EncounterDiagnosisRepository;
import com.bellidental.persistence.repository.EncounterRepository;
import com.bellidental.persistence.repository.Icd10CodeRepository;
import com.bellidental.shared.enums.EncounterStatus;
import com.bellidental.shared.exception.BusinessException;
import com.bellidental.shared.exception.ResourceNotFoundException;
import com.bellidental.shared.time.ClinicClock;
import com.bellidental.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EncounterService {

    private final EncounterRepository encounterRepository;
    private final EncounterDiagnosisRepository diagnosisRepository;
    private final Icd10CodeRepository icd10CodeRepository;
    private final AppointmentScheduler appointmentScheduler;
    private final ClinicalRecordDirectory clinicalRecordDirectory;
    private final CurrentUser currentUser;
    private final ClinicClock clinicClock;

    @Transactional
    public EncounterResponse open(UUID appointmentId, OpenEncounterRequest request) {
        UUID professionalId = currentUser.professionalId()
                .orElseThrow(() -> new BusinessException("El usuario actual no es un profesional"));

        AppointmentSummary appointment = appointmentScheduler.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada: " + appointmentId));

        boolean encounterExists = encounterRepository.existsByAppointmentId(appointmentId);
        EncounterPolicy.checkCanOpen(appointment.status(), appointment.checkedInAt(), encounterExists);

        Encounter encounter = Encounter.builder()
                .appointmentId(appointmentId)
                .professionalId(professionalId)
                .startAt(clinicClock.now())
                .chiefComplaint(request.chiefComplaint())
                .build();
        try {
            encounter = encounterRepository.saveAndFlush(encounter);
        } catch (DataIntegrityViolationException ex) {
            if (isConstraintViolation(ex, "encounters_appointment_id_key")) {
                throw new BusinessException("La cita ya tiene una atención médica registrada.");
            }
            throw ex;
        }

        clinicalRecordDirectory.openIfAbsent(appointment.patientId(), professionalId);

        return toResponse(encounter, appointment.patientId(), List.of());
    }

    @Transactional
    public EncounterResponse update(UUID encounterId, UpdateEncounterRequest request) {
        Encounter encounter = findEncounterOrThrow(encounterId);
        UUID professionalId = currentUser.professionalId()
                .orElseThrow(() -> new BusinessException("El usuario actual no es un profesional"));

        EncounterPolicy.checkCanModify(encounter.getProfessionalId(), professionalId, encounter.getVoidedAt() != null);

        encounter.setChiefComplaint(request.chiefComplaint());
        encounter.setCurrentIllness(request.currentIllness());
        encounter.setBpSystolic(request.bpSystolic());
        encounter.setBpDiastolic(request.bpDiastolic());
        encounter.setHeartRate(request.heartRate());
        encounter.setTemperature(request.temperature());
        encounter.setRespiratoryRate(request.respiratoryRate());
        encounter.setStomatognathicExam(request.stomatognathicExam());
        encounter.setPeriodontalDisease(request.periodontalDisease());
        encounter.setFluorosis(request.fluorosis());
        encounter.setMalocclusion(request.malocclusion());
        encounter.setDiagnosticPlan(request.diagnosticPlan());
        encounter.setTherapeuticPlan(request.therapeuticPlan());
        encounter.setEducationalPlan(request.educationalPlan());

        encounter = encounterRepository.save(encounter);

        UUID patientId = getPatientId(encounter.getAppointmentId());
        List<DiagnosisResponse> diagnoses = buildDiagnoses(encounterId);
        return toResponse(encounter, patientId, diagnoses);
    }

    @Transactional
    public DiagnosisResponse addDiagnosis(UUID encounterId, AddDiagnosisRequest request) {
        Encounter encounter = findEncounterOrThrow(encounterId);
        UUID professionalId = currentUser.professionalId()
                .orElseThrow(() -> new BusinessException("El usuario actual no es un profesional"));

        Icd10Code icd10Code = icd10CodeRepository.findById(request.icd10Id())
                .filter(Icd10Code::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Código CIE-10 no encontrado o inactivo: " + request.icd10Id()));

        EncounterPolicy.checkCanModify(encounter.getProfessionalId(), professionalId, encounter.getVoidedAt() != null);

        if (diagnosisRepository.existsByEncounterIdAndIcd10IdAndVoidedAtIsNull(encounterId, request.icd10Id())) {
            throw new BusinessException("El diagnóstico ya está activo en esta atención");
        }

        EncounterDiagnosis diagnosis = EncounterDiagnosis.builder()
                .encounterId(encounterId)
                .icd10Id(request.icd10Id())
                .certainty(request.certainty())
                .notes(request.notes())
                .build();
        try {
            diagnosis = diagnosisRepository.saveAndFlush(diagnosis);
        } catch (DataIntegrityViolationException ex) {
            if (isConstraintViolation(ex, "idx_encounter_diagnoses_active")) {
                throw new BusinessException("El diagnóstico ya está activo en esta atención");
            }
            throw ex;
        }

        return toDiagnosisResponse(diagnosis, icd10Code);
    }

    @Transactional
    public void voidDiagnosis(UUID diagnosisId) {
        EncounterDiagnosis diagnosis = diagnosisRepository.findById(diagnosisId)
                .orElseThrow(() -> new ResourceNotFoundException("Diagnóstico no encontrado: " + diagnosisId));

        if (diagnosis.getVoidedAt() != null) {
            throw new BusinessException("El diagnóstico ya está anulado");
        }

        UUID professionalId = currentUser.professionalId()
                .orElseThrow(() -> new BusinessException("El usuario actual no es un profesional"));

        Encounter encounter = findEncounterOrThrow(diagnosis.getEncounterId());
        EncounterPolicy.checkCanModify(encounter.getProfessionalId(), professionalId, encounter.getVoidedAt() != null);

        diagnosis.setVoidedAt(clinicClock.now());
        diagnosisRepository.save(diagnosis);
    }

    @Transactional
    public EncounterResponse complete(UUID encounterId) {
        Encounter encounter = findEncounterOrThrow(encounterId);
        UUID professionalId = currentUser.professionalId()
                .orElseThrow(() -> new BusinessException("El usuario actual no es un profesional"));

        EncounterPolicy.checkCanModify(encounter.getProfessionalId(), professionalId, encounter.getVoidedAt() != null);

        long activeDiagnoses = diagnosisRepository.countByEncounterIdAndVoidedAtIsNull(encounterId);
        EncounterPolicy.checkCanComplete(encounter.getStatus(), activeDiagnoses);

        encounter.setStatus(EncounterStatus.COMPLETED);
        encounter.setEndAt(clinicClock.now());
        encounter.setClosedAt(clinicClock.now());
        encounter = encounterRepository.save(encounter);

        appointmentScheduler.markAttended(encounter.getAppointmentId());

        UUID patientId = getPatientId(encounter.getAppointmentId());
        List<DiagnosisResponse> diagnoses = buildDiagnoses(encounterId);
        return toResponse(encounter, patientId, diagnoses);
    }

    @Transactional
    public EncounterResponse voidEncounter(UUID encounterId, VoidEncounterRequest request) {
        Encounter encounter = findEncounterOrThrow(encounterId);
        UUID professionalId = currentUser.professionalId()
                .orElseThrow(() -> new BusinessException("El usuario actual no es un profesional"));

        EncounterPolicy.checkCanModify(encounter.getProfessionalId(), professionalId, encounter.getVoidedAt() != null);

        encounter.setVoidedAt(clinicClock.now());
        encounter.setVoidReason(request.voidReason());
        encounter = encounterRepository.save(encounter);

        UUID patientId = getPatientId(encounter.getAppointmentId());
        List<DiagnosisResponse> diagnoses = buildDiagnoses(encounterId);
        return toResponse(encounter, patientId, diagnoses);
    }

    @Transactional(readOnly = true)
    public EncounterResponse findById(UUID encounterId) {
        Encounter encounter = findEncounterOrThrow(encounterId);
        UUID patientId = getPatientId(encounter.getAppointmentId());
        List<DiagnosisResponse> diagnoses = buildDiagnoses(encounterId);
        return toResponse(encounter, patientId, diagnoses);
    }

    @Transactional(readOnly = true)
    public EncounterSummary requireModifiableEncounter(UUID encounterId) {
        Encounter encounter = findEncounterOrThrow(encounterId);
        UUID professionalId = currentUser.professionalId()
                .orElseThrow(() -> new BusinessException("El usuario actual no es un profesional"));

        EncounterPolicy.checkCanModify(encounter.getProfessionalId(), professionalId, encounter.getVoidedAt() != null);

        UUID patientId = getPatientId(encounter.getAppointmentId());
        return toSummary(encounter, patientId);
    }

    public EncounterSummary toSummary(Encounter encounter, UUID patientId) {
        return new EncounterSummary(
                encounter.getId(),
                encounter.getAppointmentId(),
                patientId,
                encounter.getProfessionalId(),
                encounter.getStatus(),
                encounter.getClosedAt(),
                encounter.getVoidedAt() != null,
                encounter.getStartAt()
        );
    }

    private Encounter findEncounterOrThrow(UUID encounterId) {
        return encounterRepository.findById(encounterId)
                .orElseThrow(() -> new ResourceNotFoundException("Atención no encontrada: " + encounterId));
    }

    private UUID getPatientId(UUID appointmentId) {
        return appointmentScheduler.findById(appointmentId)
                .map(AppointmentSummary::patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada: " + appointmentId));
    }

    private List<DiagnosisResponse> buildDiagnoses(UUID encounterId) {
        return diagnosisRepository.findByEncounterId(encounterId).stream()
                .map(d -> {
                    Icd10Code code = icd10CodeRepository.findById(d.getIcd10Id()).orElse(null);
                    return toDiagnosisResponse(d, code);
                })
                .toList();
    }

    private DiagnosisResponse toDiagnosisResponse(EncounterDiagnosis diagnosis, Icd10Code icd10Code) {
        return new DiagnosisResponse(
                diagnosis.getId(),
                diagnosis.getIcd10Id(),
                icd10Code != null ? icd10Code.getCode() : null,
                icd10Code != null ? icd10Code.getDescription() : null,
                diagnosis.getCertainty(),
                diagnosis.getNotes(),
                diagnosis.getCreatedAt(),
                diagnosis.getVoidedAt()
        );
    }

    private EncounterResponse toResponse(Encounter encounter, UUID patientId, List<DiagnosisResponse> diagnoses) {
        return new EncounterResponse(
                encounter.getId(),
                encounter.getAppointmentId(),
                patientId,
                encounter.getProfessionalId(),
                encounter.getStatus(),
                encounter.getStartAt(),
                encounter.getEndAt(),
                encounter.getClosedAt(),
                encounter.getChiefComplaint(),
                encounter.getCurrentIllness(),
                encounter.getBpSystolic(),
                encounter.getBpDiastolic(),
                encounter.getHeartRate(),
                encounter.getTemperature(),
                encounter.getRespiratoryRate(),
                encounter.getStomatognathicExam(),
                encounter.getPeriodontalDisease(),
                encounter.getFluorosis(),
                encounter.getMalocclusion(),
                encounter.getDiagnosticPlan(),
                encounter.getTherapeuticPlan(),
                encounter.getEducationalPlan(),
                encounter.getVoidedAt(),
                encounter.getVoidReason(),
                diagnoses
        );
    }

    private boolean isConstraintViolation(DataIntegrityViolationException exception, String constraintNameTarget) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String constraintName = violation.getConstraintName();
                String message = violation.getMessage();
                return constraintNameTarget.equalsIgnoreCase(constraintName)
                        || (constraintName == null && message != null && message.toLowerCase().contains(constraintNameTarget.toLowerCase()));
            }
        }
        return false;
    }
}
