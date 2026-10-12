package com.bellidental.appointments.application;

import com.bellidental.appointments.AppointmentEvent;
import com.bellidental.appointments.AppointmentEventType;
import com.bellidental.appointments.api.dto.CreateAppointmentRequest;
import com.bellidental.appointments.api.dto.RescheduleAppointmentRequest;
import com.bellidental.appointments.domain.AppointmentOverlapException;
import com.bellidental.patients.PatientDirectory;
import com.bellidental.patients.PatientSummary;
import com.bellidental.persistence.entity.Appointment;
import com.bellidental.persistence.entity.ProfessionalSchedule;
import com.bellidental.persistence.repository.AppointmentRepository;
import com.bellidental.persistence.repository.EncounterRepository;
import com.bellidental.persistence.repository.ProfessionalScheduleRepository;
import com.bellidental.professionals.ProfessionalDirectory;
import com.bellidental.professionals.SpecialtySummary;
import com.bellidental.security.CurrentUser;
import com.bellidental.shared.enums.AppointmentStatus;
import com.bellidental.shared.exception.BusinessException;
import com.bellidental.shared.exception.InvalidInputException;
import com.bellidental.shared.time.ClinicClock;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private ProfessionalScheduleRepository scheduleRepository;

    @Mock
    private EncounterRepository encounterRepository;

    @Mock
    private PatientDirectory patientDirectory;

    @Mock
    private ProfessionalDirectory professionalDirectory;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private ClinicClock clock;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AppointmentService appointmentService;

    private UUID patientId;
    private UUID professionalId;
    private UUID userId;
    private OffsetDateTime now;

    @BeforeEach
    void setUp() {
        patientId = UUID.randomUUID();
        professionalId = UUID.randomUUID();
        userId = UUID.randomUUID();
        now = OffsetDateTime.now();
    }

    @Test
    void create_pastStartAt_throwsInvalidInput() {
        when(patientDirectory.findById(patientId)).thenReturn(Optional.of(new PatientSummary(patientId, null, null, null, null, null, null, null, true)));
        when(professionalDirectory.findSpecialty(1L)).thenReturn(Optional.of(new SpecialtySummary(1L, "General", 30, true)));
        when(professionalDirectory.hasActiveSpecialty(professionalId, 1L)).thenReturn(true);
        when(clock.now()).thenReturn(now);

        CreateAppointmentRequest req = new CreateAppointmentRequest(patientId, professionalId, 1L, now.minusMinutes(10), null, null, null);

        assertThatThrownBy(() -> appointmentService.create(req))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("La cita no puede empezar en el pasado.");
    }

    @Test
    void create_receptionOutOfSchedule_throwsBusinessException() {
        when(patientDirectory.findById(patientId)).thenReturn(Optional.of(new PatientSummary(patientId, null, null, null, null, null, null, null, true)));
        when(professionalDirectory.findSpecialty(1L)).thenReturn(Optional.of(new SpecialtySummary(1L, "General", 30, true)));
        when(professionalDirectory.hasActiveSpecialty(professionalId, 1L)).thenReturn(true);
        when(clock.now()).thenReturn(now);
        when(clock.zone()).thenReturn(ZoneId.systemDefault());
        when(currentUser.professionalId()).thenReturn(Optional.empty());
        when(scheduleRepository.findByProfessionalIdAndActiveTrue(professionalId)).thenReturn(List.of());

        CreateAppointmentRequest req = new CreateAppointmentRequest(patientId, professionalId, 1L, now.plusDays(1), null, null, null);

        assertThatThrownBy(() -> appointmentService.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessage("El horario solicitado está fuera del horario efectivo del profesional.");
    }

    @Test
    void create_receptionCrossesEndOfShift_throwsBusinessException() {
        when(patientDirectory.findById(patientId)).thenReturn(Optional.of(new PatientSummary(patientId, null, null, null, null, null, null, null, true)));
        when(professionalDirectory.findSpecialty(1L)).thenReturn(Optional.of(new SpecialtySummary(1L, "General", 30, true)));
        when(professionalDirectory.hasActiveSpecialty(professionalId, 1L)).thenReturn(true);
        when(clock.now()).thenReturn(now);
        when(clock.zone()).thenReturn(ZoneId.systemDefault());
        when(currentUser.professionalId()).thenReturn(Optional.empty());

        OffsetDateTime start = now.plusDays(1).withHour(11).withMinute(45).withSecond(0).withNano(0);
        
        ProfessionalSchedule schedule = ProfessionalSchedule.builder()
                .weekday((short) start.getDayOfWeek().getValue())
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(12, 0))
                .available(true)
                .build();

        when(scheduleRepository.findByProfessionalIdAndActiveTrue(professionalId)).thenReturn(List.of(schedule));

        CreateAppointmentRequest req = new CreateAppointmentRequest(patientId, professionalId, 1L, start, null, null, null);

        assertThatThrownBy(() -> appointmentService.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessage("El horario solicitado está fuera del horario efectivo del profesional.");
    }

    @Test
    void create_professionalOutOfSchedule_createsAppointment() {
        when(patientDirectory.findById(patientId)).thenReturn(Optional.of(new PatientSummary(patientId, null, null, null, null, null, null, null, true)));
        when(professionalDirectory.findSpecialty(1L)).thenReturn(Optional.of(new SpecialtySummary(1L, "General", 30, true)));
        when(professionalDirectory.hasActiveSpecialty(professionalId, 1L)).thenReturn(true);
        when(clock.now()).thenReturn(now);
        when(currentUser.professionalId()).thenReturn(Optional.of(professionalId));
        when(currentUser.userId()).thenReturn(userId);

        Appointment saved = Appointment.builder().id(UUID.randomUUID()).build();
        when(appointmentRepository.saveAndFlush(any())).thenReturn(saved);

        CreateAppointmentRequest req = new CreateAppointmentRequest(patientId, professionalId, 1L, now.plusDays(1), null, null, null);

        appointmentService.create(req);

        verify(appointmentRepository).saveAndFlush(any());
    }

    @Test
    void create_withoutEndAt_usesSpecialtyDefaultDuration() {
        when(patientDirectory.findById(patientId)).thenReturn(Optional.of(new PatientSummary(patientId, null, null, null, null, null, null, null, true)));
        when(professionalDirectory.findSpecialty(1L)).thenReturn(Optional.of(new SpecialtySummary(1L, "General", 30, true)));
        when(professionalDirectory.hasActiveSpecialty(professionalId, 1L)).thenReturn(true);
        when(clock.now()).thenReturn(now);
        when(currentUser.professionalId()).thenReturn(Optional.of(professionalId));
        when(currentUser.userId()).thenReturn(userId);

        Appointment saved = Appointment.builder().id(UUID.randomUUID()).build();
        when(appointmentRepository.saveAndFlush(any())).thenReturn(saved);

        CreateAppointmentRequest req = new CreateAppointmentRequest(patientId, professionalId, 1L, now.plusDays(1), null, null, null);

        appointmentService.create(req);

        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).saveAndFlush(captor.capture());
        Appointment savedAppt = captor.getValue();
        assertThat(savedAppt.getEndAt()).isEqualTo(now.plusDays(1).plusMinutes(30));
    }

    @Test
    void create_constraintViolation_throwsAppointmentOverlapException() {
        when(patientDirectory.findById(patientId)).thenReturn(Optional.of(new PatientSummary(patientId, null, null, null, null, null, null, null, true)));
        when(professionalDirectory.findSpecialty(1L)).thenReturn(Optional.of(new SpecialtySummary(1L, "General", 30, true)));
        when(professionalDirectory.hasActiveSpecialty(professionalId, 1L)).thenReturn(true);
        when(clock.now()).thenReturn(now);
        when(currentUser.professionalId()).thenReturn(Optional.of(professionalId));
        when(currentUser.userId()).thenReturn(userId);

        ConstraintViolationException cause = new ConstraintViolationException("appointments_no_overlap violation", null, "appointments_no_overlap");
        when(appointmentRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("error", cause));

        CreateAppointmentRequest req = new CreateAppointmentRequest(patientId, professionalId, 1L, now.plusDays(1), null, null, null);

        assertThatThrownBy(() -> appointmentService.create(req))
                .isInstanceOf(AppointmentOverlapException.class)
                .hasMessage("El profesional ya tiene una cita en ese horario.");
    }

    @Test
    void reschedule_updatesOriginalAndCreatesNew() {
        UUID originalId = UUID.randomUUID();
        Appointment original = Appointment.builder()
                .id(originalId)
                .patientId(patientId)
                .professionalId(professionalId)
                .specialtyId(1L)
                .startAt(now.plusDays(1))
                .endAt(now.plusDays(1).plusMinutes(30))
                .status(AppointmentStatus.PENDING)
                .build();

        when(appointmentRepository.findById(originalId)).thenReturn(Optional.of(original));
        
        when(patientDirectory.findById(patientId)).thenReturn(Optional.of(new PatientSummary(patientId, null, null, null, null, null, null, null, true)));
        when(professionalDirectory.findSpecialty(1L)).thenReturn(Optional.of(new SpecialtySummary(1L, "General", 30, true)));
        when(professionalDirectory.hasActiveSpecialty(professionalId, 1L)).thenReturn(true);
        when(clock.now()).thenReturn(now);
        when(currentUser.userId()).thenReturn(userId);

        Appointment newAppt = Appointment.builder().id(UUID.randomUUID()).build();
        when(appointmentRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            if (a.getId() == originalId) return a;
            return newAppt;
        });

        RescheduleAppointmentRequest request = new RescheduleAppointmentRequest(now.plusDays(2), null, null, null);

        appointmentService.reschedule(originalId, request);

        assertThat(original.getStatus()).isEqualTo(AppointmentStatus.RESCHEDULED);
        
        ArgumentCaptor<AppointmentEvent> eventCaptor = ArgumentCaptor.forClass(AppointmentEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        
        assertThat(eventCaptor.getValue().type()).isEqualTo(AppointmentEventType.RESCHEDULED);
        assertThat(eventCaptor.getValue().previousAppointmentId()).isEqualTo(originalId);
    }
}
