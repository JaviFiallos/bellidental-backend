package com.bellidental.appointments.application;

import com.bellidental.appointments.AppointmentEvent;
import com.bellidental.appointments.AppointmentEventType;
import com.bellidental.appointments.api.dto.AppointmentResponse;
import com.bellidental.appointments.api.dto.CreateAppointmentRequest;
import com.bellidental.appointments.api.dto.RescheduleAppointmentRequest;
import com.bellidental.appointments.domain.AppointmentOverlapException;
import com.bellidental.appointments.domain.AppointmentTransitions;
import com.bellidental.appointments.domain.EffectiveScheduleResolver;
import com.bellidental.appointments.domain.ScheduleRule;
import com.bellidental.appointments.domain.Shift;
import com.bellidental.patients.PatientDirectory;
import com.bellidental.patients.PatientSummary;
import com.bellidental.persistence.entity.Appointment;
import com.bellidental.persistence.entity.ProfessionalSchedule;
import com.bellidental.persistence.repository.AppointmentRepository;
import com.bellidental.persistence.repository.ProfessionalScheduleRepository;
import com.bellidental.professionals.ProfessionalDirectory;
import com.bellidental.professionals.ProfessionalSummary;
import com.bellidental.professionals.SpecialtySummary;
import com.bellidental.security.CurrentUser;
import com.bellidental.shared.enums.AppointmentSource;
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
    private final PatientDirectory patientDirectory;
    private final ProfessionalDirectory professionalDirectory;
    private final CurrentUser currentUser;
    private final ClinicClock clock;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public AppointmentResponse create(CreateAppointmentRequest request) {
        return createInternal(request, null);
    }

    private AppointmentResponse createInternal(CreateAppointmentRequest request, Appointment originalForReschedule) {
        PatientSummary patient = patientDirectory.findById(request.patientId())
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado."));
        if (!patient.active()) {
            throw new BusinessException("El paciente no está activo.");
        }

        SpecialtySummary specialty = professionalDirectory.findSpecialty(request.specialtyId())
                .filter(SpecialtySummary::active)
                .orElseThrow(() -> new ResourceNotFoundException("Especialidad no encontrada o inactiva."));

        if (!professionalDirectory.hasActiveSpecialty(request.professionalId(), request.specialtyId())) {
            throw new BusinessException("El profesional no tiene activa esta especialidad.");
        }

        if (request.startAt().isBefore(clock.now())) {
            throw new InvalidInputException("La cita no puede empezar en el pasado.");
        }

        OffsetDateTime endAt = request.endAt();
        if (endAt == null) {
            endAt = request.startAt().plusMinutes(specialty.defaultDurationMinutes());
        }
        if (!endAt.isAfter(request.startAt())) {
            throw new InvalidInputException("El fin de la cita debe ser posterior al inicio.");
        }

        AppointmentSource source;
        if (currentUser.professionalId().isPresent()) {
            source = AppointmentSource.PROFESSIONAL;
        } else {
            source = AppointmentSource.RECEPTION;
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
                .createdBy(currentUser.userId())
                .rescheduledFromId(originalForReschedule != null ? originalForReschedule.getId() : null)
                .build();

        try {
            appointment = appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException ex) {
            if (isOverlapViolation(ex)) {
                throw new AppointmentOverlapException("El profesional ya tiene una cita en ese horario.");
            }
            throw ex;
        }

        AppointmentEventType eventType = originalForReschedule != null ? AppointmentEventType.RESCHEDULED : AppointmentEventType.CREATED;
        UUID previousId = originalForReschedule != null ? originalForReschedule.getId() : null;
        eventPublisher.publishEvent(new AppointmentEvent(appointment.getId(), eventType, previousId));

        ProfessionalSummary professional = professionalDirectory.findById(request.professionalId()).orElse(null);
        return toResponse(appointment, fullName(patient), professional, specialty.name());
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
    public AppointmentResponse reschedule(UUID id, RescheduleAppointmentRequest request) {
        Appointment original = getAppointment(id);
        original.setStatus(AppointmentTransitions.reschedule(original.getStatus(), original.getCheckedInAt() != null));
        appointmentRepository.saveAndFlush(original);

        CreateAppointmentRequest newReq = new CreateAppointmentRequest(
                original.getPatientId(),
                request.professionalId() != null ? request.professionalId() : original.getProfessionalId(),
                request.specialtyId() != null ? request.specialtyId() : original.getSpecialtyId(),
                request.startAt(),
                request.endAt(),
                original.getReason(),
                original.getNotes()
        );

        return createInternal(newReq, original);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointments(UUID professionalId, OffsetDateTime from, OffsetDateTime to) {
        if (to.isBefore(from) || to.equals(from)) {
            throw new InvalidInputException("La fecha 'to' debe ser posterior a 'from'.");
        }
        if (ChronoUnit.DAYS.between(from, to) > 31) {
            throw new InvalidInputException("El rango no puede ser mayor a 31 días.");
        }

        List<Appointment> appointments;
        if (professionalId != null) {
            appointments = appointmentRepository.findByProfessionalIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(professionalId, from, to);
        } else {
            appointments = appointmentRepository.findByStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(from, to);
        }

        List<UUID> patientIds = appointments.stream().map(Appointment::getPatientId).distinct().toList();
        Map<UUID, String> patientNames = patientDirectory.findByIds(patientIds).stream()
                .collect(Collectors.toMap(PatientSummary::id, this::fullName));

        Map<UUID, ProfessionalSummary> activeProfessionals = professionalDirectory.findActive().stream()
                .collect(Collectors.toMap(ProfessionalSummary::id, Function.identity()));
                
        Map<Long, String> specialties = new java.util.HashMap<>();

        return appointments.stream().map(appointment -> {
            String patientName = patientNames.getOrDefault(appointment.getPatientId(), "Desconocido");
            ProfessionalSummary professional = activeProfessionals.get(appointment.getProfessionalId());
            String specialtyName = specialties.computeIfAbsent(appointment.getSpecialtyId(), id -> 
                    professionalDirectory.findSpecialty(id).map(SpecialtySummary::name).orElse("Desconocido"));
            return toResponse(appointment, patientName, professional, specialtyName);
        }).toList();
    }

    @Transactional(readOnly = true)
    public AppointmentResponse getById(UUID id) {
        Appointment appointment = getAppointment(id);
        String patientName = patientDirectory.findById(appointment.getPatientId())
                .map(this::fullName).orElse("Desconocido");
        ProfessionalSummary professional = professionalDirectory.findById(appointment.getProfessionalId()).orElse(null);
        String specialtyName = professionalDirectory.findSpecialty(appointment.getSpecialtyId())
                .map(SpecialtySummary::name).orElse("Desconocido");

        return toResponse(appointment, patientName, professional, specialtyName);
    }

    private Appointment getAppointment(UUID id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada."));
    }
    private String fullName(PatientSummary p) {
        return p.firstName() + " " + p.lastName();
    }

    private AppointmentResponse toResponse(Appointment appointment, String patientName, ProfessionalSummary professional, String specialtyName) {
        String professionalName = professional != null ? professional.firstName() + " " + professional.lastName() : "Desconocido";
        String colorTag = professional != null ? professional.colorTag() : null;

        return new AppointmentResponse(
                appointment.getId(), appointment.getPatientId(), patientName,
                appointment.getProfessionalId(), professionalName, colorTag,
                appointment.getSpecialtyId(), specialtyName,
                appointment.getStartAt(), appointment.getEndAt(), appointment.getStatus(), appointment.getSource(),
                appointment.getReason(), appointment.getNotes(), appointment.getCheckedInAt(), appointment.getRescheduledFromId()
        );
    }
}
