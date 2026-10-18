package com.bellidental.appointments.application;

import com.bellidental.appointments.AppointmentEvent;
import com.bellidental.appointments.AppointmentEventType;
import com.bellidental.appointments.api.dto.AppointmentResponse;
import com.bellidental.appointments.api.dto.CreateAppointmentRequest;
import com.bellidental.appointments.api.dto.RescheduleAppointmentRequest;
import com.bellidental.appointments.api.dto.WalkInRequest;
import com.bellidental.appointments.domain.AppointmentDuration;
import com.bellidental.appointments.domain.AppointmentOverlapException;
import com.bellidental.appointments.domain.AppointmentTransitions;
import com.bellidental.appointments.domain.EffectiveScheduleResolver;
import com.bellidental.appointments.domain.ScheduleRule;
import com.bellidental.appointments.domain.Shift;
import com.bellidental.appointments.domain.TimeRange;
import com.bellidental.appointments.domain.WalkInSlotFinder;
import com.bellidental.patients.PatientDirectory;
import com.bellidental.patients.PatientSummary;
import com.bellidental.persistence.entity.Appointment;
import com.bellidental.persistence.entity.Encounter;
import com.bellidental.persistence.entity.ProfessionalSchedule;
import com.bellidental.persistence.repository.AppointmentRepository;
import com.bellidental.persistence.repository.EncounterRepository;
import com.bellidental.persistence.repository.ProfessionalScheduleRepository;
import com.bellidental.professionals.ProfessionalDirectory;
import com.bellidental.professionals.ProfessionalSummary;
import com.bellidental.professionals.SpecialtySummary;
import com.bellidental.security.CurrentUser;
import com.bellidental.shared.enums.AppointmentSource;
import com.bellidental.shared.enums.AppointmentStatus;
import com.bellidental.shared.enums.EncounterStatus;
import com.bellidental.shared.exception.BusinessException;
import com.bellidental.shared.exception.InvalidInputException;
import com.bellidental.shared.exception.ResourceNotFoundException;
import com.bellidental.shared.time.ClinicClock;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private static final String OVERLAP_CONSTRAINT = "appointments_no_overlap";

    private final AppointmentRepository appointmentRepository;
    private final ProfessionalScheduleRepository scheduleRepository;
    private final EncounterRepository encounterRepository;
    private final PatientDirectory patientDirectory;
    private final ProfessionalDirectory professionalDirectory;
    private final AvailabilityService availabilityService;
    private final CurrentUser currentUser;
    private final ClinicClock clock;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public AppointmentResponse create(CreateAppointmentRequest request) {
        AppointmentSource source = currentUser.professionalId().isPresent()
                ? AppointmentSource.PROFESSIONAL
                : AppointmentSource.RECEPTION;
        return book(request, source, currentUser.userId());
    }

    /** Reglas de creación comunes a HTTP, bot y reprogramación. */
    @Transactional
    public AppointmentResponse book(CreateAppointmentRequest request, AppointmentSource source, UUID createdBy) {
        return book(request, source, createdBy, null);
    }

    private AppointmentResponse book(CreateAppointmentRequest request, AppointmentSource source, UUID createdBy, Appointment original) {
        PatientSummary patient = loadActivePatient(request.patientId());
        SpecialtySummary specialty = loadBookableSpecialty(request.professionalId(), request.specialtyId());

        if (request.startAt().isBefore(clock.now())) {
            throw new InvalidInputException("La cita no puede empezar en el pasado.");
        }

        OffsetDateTime endAt = request.endAt();
        if (endAt != null) {
            AppointmentDuration.validate(request.startAt(), endAt);
        } else {
            endAt = request.startAt().plusMinutes(specialty.defaultDurationMinutes());
        }
        if (!endAt.isAfter(request.startAt())) {
            throw new InvalidInputException("El fin de la cita debe ser posterior al inicio.");
        }

        if (source == AppointmentSource.RECEPTION || source == AppointmentSource.BOT) {
            validateSchedule(request.professionalId(), request.startAt(), endAt);
        }

        Appointment appointment = Appointment.builder()
                .patientId(request.patientId())
                .professionalId(request.professionalId())
                .specialtyId(request.specialtyId())
                .startAt(request.startAt())
                .endAt(endAt)
                .source(source)
                .reason(request.reason())
                .notes(request.notes())
                .createdBy(createdBy)
                .rescheduledFromId(original != null ? original.getId() : null)
                .build();

        AppointmentEventType eventType = original != null ? AppointmentEventType.RESCHEDULED : AppointmentEventType.CREATED;
        return persistAndRespond(appointment, patient, specialty, eventType, original != null ? original.getId() : null);
    }

    /** Atención sin cita: primer hueco desde ahora hasta el fin del día local, sin horario del profesional. */
    @Transactional
    public AppointmentResponse walkIn(WalkInRequest request) {
        PatientSummary patient = loadActivePatient(request.patientId());
        SpecialtySummary specialty = loadBookableSpecialty(request.professionalId(), request.specialtyId());

        OffsetDateTime now = clock.now();
        OffsetDateTime endOfDay = clock.today().atTime(23, 59).atZone(clock.zone()).toOffsetDateTime();
        List<TimeRange> busy = availabilityService.findBusyRanges(request.professionalId(), now, endOfDay);

        OffsetDateTime startAt = WalkInSlotFinder.findFirst(now, specialty.defaultDurationMinutes(), busy, endOfDay)
                .orElseThrow(() -> new BusinessException("Sin hueco hoy para este profesional."));

        Appointment appointment = Appointment.builder()
                .patientId(request.patientId())
                .professionalId(request.professionalId())
                .specialtyId(request.specialtyId())
                .startAt(startAt)
                .endAt(startAt.plusMinutes(specialty.defaultDurationMinutes()))
                .source(AppointmentSource.WALK_IN)
                .status(AppointmentStatus.PENDING)
                .checkedInAt(now)
                .createdBy(currentUser.userId())
                .build();

        return persistAndRespond(appointment, patient, specialty, AppointmentEventType.CREATED, null);
    }

    private PatientSummary loadActivePatient(UUID patientId) {
        PatientSummary patient = patientDirectory.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado."));
        if (!patient.active()) {
            throw new BusinessException("El paciente no está activo.");
        }
        return patient;
    }

    private SpecialtySummary loadBookableSpecialty(UUID professionalId, Long specialtyId) {
        SpecialtySummary specialty = professionalDirectory.findSpecialty(specialtyId)
                .filter(SpecialtySummary::active)
                .orElseThrow(() -> new ResourceNotFoundException("Especialidad no encontrada o inactiva."));
        if (!professionalDirectory.hasActiveSpecialty(professionalId, specialtyId)) {
            throw new BusinessException("El profesional no tiene activa esta especialidad.");
        }
        return specialty;
    }

    private AppointmentResponse persistAndRespond(Appointment appointment, PatientSummary patient, SpecialtySummary specialty,
                                                  AppointmentEventType eventType, UUID previousId) {
        Appointment saved = saveOrTranslateOverlap(appointment);
        eventPublisher.publishEvent(new AppointmentEvent(saved.getId(), eventType, previousId));

        ProfessionalSummary professional = professionalDirectory.findById(saved.getProfessionalId()).orElse(null);
        return toResponse(saved, fullName(patient), professional, specialty.name(), null);
    }

    private Appointment saveOrTranslateOverlap(Appointment appointment) {
        try {
            return appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException ex) {
            if (isOverlapViolation(ex)) {
                throw new AppointmentOverlapException("El profesional ya tiene una cita en ese horario.");
            }
            throw ex;
        }
    }

    private boolean isOverlapViolation(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String constraintName = violation.getConstraintName();
                String message = violation.getMessage();
                return OVERLAP_CONSTRAINT.equals(constraintName)
                        || (constraintName == null && message != null && message.contains(OVERLAP_CONSTRAINT));
            }
        }
        return false;
    }

    private void validateSchedule(UUID professionalId, OffsetDateTime startAt, OffsetDateTime endAt) {
        LocalDate date = startAt.atZoneSameInstant(clock.zone()).toLocalDate();
        LocalTime startTime = startAt.atZoneSameInstant(clock.zone()).toLocalTime();
        LocalTime endTime = endAt.atZoneSameInstant(clock.zone()).toLocalTime();

        List<ProfessionalSchedule> schedules = scheduleRepository.findByProfessionalIdAndActiveTrue(professionalId);
        List<ScheduleRule> activeSchedules = schedules.stream().map(ScheduleService::toRule).toList();
        List<Shift> shifts = EffectiveScheduleResolver.resolve(date, activeSchedules);

        boolean fitsInAnyShift = shifts.stream().anyMatch(shift ->
            !startTime.isBefore(shift.startTime()) && !endTime.isAfter(shift.endTime())
        );

        if (!fitsInAnyShift) {
            throw new BusinessException("El horario solicitado está fuera del horario efectivo del profesional.");
        }
    }

    @Transactional
    public void confirm(UUID id) {
        Appointment appointment = getAppointment(id);
        appointment.setStatus(AppointmentTransitions.confirm(appointment.getStatus()));
        appointmentRepository.save(appointment);
        eventPublisher.publishEvent(new AppointmentEvent(id, AppointmentEventType.STATUS_CHANGED, null));
    }

    @Transactional
    public void cancel(UUID id) {
        Appointment appointment = getAppointment(id);
        appointment.setStatus(AppointmentTransitions.cancel(appointment.getStatus()));
        appointmentRepository.save(appointment);
        eventPublisher.publishEvent(new AppointmentEvent(id, AppointmentEventType.CANCELLED, null));
    }

    @Transactional
    public void checkIn(UUID id) {
        Appointment appointment = getAppointment(id);
        appointment.setStatus(AppointmentTransitions.checkIn(appointment.getStatus(), appointment.getCheckedInAt() != null));
        appointment.setCheckedInAt(clock.now());
        appointmentRepository.save(appointment);
        eventPublisher.publishEvent(new AppointmentEvent(id, AppointmentEventType.STATUS_CHANGED, null));
    }

    @Transactional
    public void noShow(UUID id) {
        Appointment appointment = getAppointment(id);
        appointment.setStatus(AppointmentTransitions.noShow(appointment.getStatus(), appointment.getCheckedInAt() != null));
        appointmentRepository.save(appointment);
        eventPublisher.publishEvent(new AppointmentEvent(id, AppointmentEventType.STATUS_CHANGED, null));
    }

    @Transactional
    public void markAttended(UUID id) {
        Appointment appointment = getAppointment(id);
        appointment.setStatus(AppointmentTransitions.attend(appointment.getStatus(), appointment.getCheckedInAt() != null));
        appointmentRepository.save(appointment);
        eventPublisher.publishEvent(new AppointmentEvent(id, AppointmentEventType.STATUS_CHANGED, null));
    }

    @Transactional
    public AppointmentResponse reschedule(UUID id, RescheduleAppointmentRequest request) {
        return reschedule(id, request, currentUser.userId());
    }

    @Transactional
    public AppointmentResponse reschedule(UUID id, RescheduleAppointmentRequest request, UUID requestedBy) {
        Appointment original = getAppointment(id);
        original.setStatus(AppointmentTransitions.reschedule(original.getStatus(), original.getCheckedInAt() != null));
        appointmentRepository.saveAndFlush(original);

        CreateAppointmentRequest newRequest = new CreateAppointmentRequest(
                original.getPatientId(),
                request.professionalId() != null ? request.professionalId() : original.getProfessionalId(),
                request.specialtyId() != null ? request.specialtyId() : original.getSpecialtyId(),
                request.startAt(),
                request.endAt(),
                original.getReason(),
                original.getNotes()
        );

        return book(newRequest, original.getSource(), requestedBy, original);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointments(UUID professionalId, UUID patientId, OffsetDateTime from, OffsetDateTime to) {
        if (patientId != null && professionalId != null) {
            throw new InvalidInputException("Filtra por paciente o por profesional, no por ambos.");
        }

        List<Appointment> appointments;
        if (patientId != null) {
            if ((from == null && to != null) || (from != null && to == null)) {
                throw new InvalidInputException("Indica 'from' y 'to' juntos.");
            }
            if (from == null && to == null) {
                appointments = appointmentRepository.findByPatientIdOrderByStartAtAsc(patientId);
            } else {
                validateRange(from, to);
                appointments = appointmentRepository.findByPatientIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(patientId, from, to);
            }
        } else {
            if (from == null || to == null) {
                throw new InvalidInputException("Indica 'from' y 'to'.");
            }
            validateRange(from, to);
            if (professionalId != null) {
                appointments = appointmentRepository.findByProfessionalIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(professionalId, from, to);
            } else {
                appointments = appointmentRepository.findByStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(from, to);
            }
        }

        List<UUID> patientIds = appointments.stream().map(Appointment::getPatientId).distinct().toList();
        Map<UUID, String> patientNames = patientDirectory.findByIds(patientIds).stream()
                .collect(Collectors.toMap(PatientSummary::id, this::fullName));

        Map<UUID, ProfessionalSummary> professionals = professionalDirectory.findActive().stream()
                .collect(Collectors.toMap(ProfessionalSummary::id, Function.identity(), (a, b) -> a, HashMap::new));

        Map<Long, String> specialtyNames = new HashMap<>();

        List<UUID> appointmentIds = appointments.stream().map(Appointment::getId).toList();
        Map<UUID, Encounter> encounters = appointmentIds.isEmpty()
                ? Collections.emptyMap()
                : encounterRepository.findByAppointmentIdIn(appointmentIds).stream()
                        .collect(Collectors.toMap(Encounter::getAppointmentId, Function.identity(), (a, b) -> a));

        return appointments.stream().map(appointment -> {
            String patientName = patientNames.getOrDefault(appointment.getPatientId(), "Desconocido");
            ProfessionalSummary professional = professionals.computeIfAbsent(appointment.getProfessionalId(), id ->
                    professionalDirectory.findById(id).orElse(null));
            String specialtyName = specialtyNames.computeIfAbsent(appointment.getSpecialtyId(), id ->
                    professionalDirectory.findSpecialty(id).map(SpecialtySummary::name).orElse("Desconocido"));
            Encounter encounter = encounters.get(appointment.getId());
            return toResponse(appointment, patientName, professional, specialtyName, encounter);
        }).toList();
    }

    private void validateRange(OffsetDateTime from, OffsetDateTime to) {
        if (to.isBefore(from) || to.equals(from)) {
            throw new InvalidInputException("La fecha 'to' debe ser posterior a 'from'.");
        }
        if (ChronoUnit.DAYS.between(from, to) > 31) {
            throw new InvalidInputException("El rango no puede ser mayor a 31 días.");
        }
    }

    @Transactional(readOnly = true)
    public AppointmentResponse getById(UUID id) {
        Appointment appointment = getAppointment(id);
        String patientName = patientDirectory.findById(appointment.getPatientId())
                .map(this::fullName).orElse("Desconocido");
        ProfessionalSummary professional = professionalDirectory.findById(appointment.getProfessionalId()).orElse(null);
        String specialtyName = professionalDirectory.findSpecialty(appointment.getSpecialtyId())
                .map(SpecialtySummary::name).orElse("Desconocido");
        Encounter encounter = encounterRepository.findByAppointmentId(id).orElse(null);

        return toResponse(appointment, patientName, professional, specialtyName, encounter);
    }

    private Appointment getAppointment(UUID id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada."));
    }

    private String fullName(PatientSummary patient) {
        return patient.firstName() + " " + patient.lastName();
    }

    private AppointmentResponse toResponse(Appointment appointment, String patientName, ProfessionalSummary professional, String specialtyName, Encounter encounter) {
        String professionalName = professional != null ? professional.firstName() + " " + professional.lastName() : "Desconocido";
        String colorTag = professional != null ? professional.colorTag() : null;
        UUID encounterId = encounter != null ? encounter.getId() : null;
        EncounterStatus encounterStatus = encounter != null ? encounter.getStatus() : null;
        boolean encounterVoided = encounter != null && encounter.getVoidedAt() != null;

        return new AppointmentResponse(
                appointment.getId(), appointment.getPatientId(), patientName,
                appointment.getProfessionalId(), professionalName, colorTag,
                appointment.getSpecialtyId(), specialtyName,
                appointment.getStartAt(), appointment.getEndAt(), appointment.getStatus(), appointment.getSource(),
                appointment.getReason(), appointment.getNotes(), appointment.getCheckedInAt(), appointment.getRescheduledFromId(),
                encounterId, encounterStatus, encounterVoided
        );
    }
}
