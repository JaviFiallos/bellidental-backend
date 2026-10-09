package com.bellidental.appointments;

import com.bellidental.appointments.api.dto.AppointmentResponse;
import com.bellidental.appointments.api.dto.CreateAppointmentRequest;
import com.bellidental.appointments.api.dto.ScheduleRequest;
import com.bellidental.appointments.api.dto.WalkInRequest;
import com.bellidental.identity.api.dto.AuthResponse;
import com.bellidental.identity.api.dto.LoginRequest;
import com.bellidental.patients.CreatePatientRequest;
import com.bellidental.patients.PatientSummary;
import com.bellidental.persistence.entity.Professional;
import com.bellidental.persistence.entity.ProfessionalSpecialty;
import com.bellidental.persistence.repository.ProfessionalRepository;
import com.bellidental.persistence.repository.ProfessionalSpecialtyRepository;
import com.bellidental.persistence.repository.UserRepository;
import com.bellidental.shared.enums.AppointmentSource;
import com.bellidental.shared.enums.AppointmentStatus;
import com.bellidental.shared.enums.DocumentType;
import com.bellidental.shared.exception.BusinessException;
import com.bellidental.shared.response.ApiResponse;
import com.bellidental.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AvailabilityIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private ProfessionalSpecialtyRepository specialtyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AppointmentScheduler appointmentScheduler;

    private HttpHeaders headers;
    private UUID patientId;
    private UUID professionalId;
    private Long specialtyId;
    private final ZoneId clinicZone = ZoneId.of("America/Guayaquil");

    @BeforeEach
    void setUp() {
        LoginRequest login = new LoginRequest("secretaria@bellidental.local", "Cambiar.2026!");
        ResponseEntity<ApiResponse<AuthResponse>> loginResponse = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(login),
                new ParameterizedTypeReference<>() {}
        );
        headers = new HttpHeaders();
        headers.setBearerAuth(loginResponse.getBody().getData().accessToken());

        String randomSuffix = UUID.randomUUID().toString().substring(0, 5);
        CreatePatientRequest patientReq = new CreatePatientRequest(
                DocumentType.PASAPORTE, "A17200" + randomSuffix, "Test", "Patient",
                LocalDate.of(1990, 1, 1), "M", null, "0999999999", null, null, null
        );
        ResponseEntity<ApiResponse<PatientSummary>> patientResp = restTemplate.exchange(
                "/api/v1/patients",
                HttpMethod.POST,
                new HttpEntity<>(patientReq, headers),
                new ParameterizedTypeReference<>() {}
        );
        patientId = patientResp.getBody().getData().id();

        var user = userRepository.findByUsernameAndDeletedAtIsNull("odontologa").orElseThrow();
        Professional professional = professionalRepository.findByUserId(user.getId()).orElseThrow();
        professionalId = professional.getId();

        ProfessionalSpecialty ps = specialtyRepository.findByProfessionalId(professionalId).stream()
                .filter(ProfessionalSpecialty::isActive)
                .findFirst().orElseThrow();
        specialtyId = ps.getSpecialty().getId();
    }

    private void createSchedule(LocalDate day) {
        ScheduleRequest scheduleReq = new ScheduleRequest(null, day, LocalTime.of(8, 0), LocalTime.of(20, 0), true);
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/professionals/" + professionalId + "/schedules",
                HttpMethod.POST,
                new HttpEntity<>(scheduleReq, headers),
                Void.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void getAvailability_afterBookedAppointment_skipsBusyTimeAndOffersSlotAtItsEnd() {
        LocalDate day = LocalDate.now().plusDays(14);
        createSchedule(day);

        OffsetDateTime startAt = day.atTime(10, 0).atZone(clinicZone).toOffsetDateTime();
        CreateAppointmentRequest request = new CreateAppointmentRequest(patientId, professionalId, specialtyId, startAt, null, null, null);
        ResponseEntity<ApiResponse<AppointmentResponse>> created = restTemplate.exchange(
                "/api/v1/appointments", HttpMethod.POST, new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {});
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        OffsetDateTime endAt = created.getBody().getData().endAt();

        ResponseEntity<ApiResponse<List<AvailableSlot>>> availability = restTemplate.exchange(
                "/api/v1/availability?professionalId=" + professionalId + "&specialtyId=" + specialtyId + "&date=" + day,
                HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});
        assertThat(availability.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<AvailableSlot> slots = availability.getBody().getData();

        assertThat(slots).extracting(AvailableSlot::startAt).doesNotContain(startAt);
        assertThat(slots).extracting(AvailableSlot::startAt).contains(endAt);
    }

    @Test
    void book_botOutsideScheduleThenInside_rejectsThenCreatesBotAppointment() {
        LocalDate day = LocalDate.now().plusDays(15);
        createSchedule(day);
        UUID botUserId = userRepository.findByUsernameAndDeletedAtIsNull("bot_whatsapp").orElseThrow().getId();

        BookAppointmentCommand outside = new BookAppointmentCommand(patientId, professionalId, specialtyId,
                day.atTime(21, 0).atZone(clinicZone).toOffsetDateTime(), AppointmentSource.BOT, botUserId, null);
        assertThatThrownBy(() -> appointmentScheduler.book(outside)).isInstanceOf(BusinessException.class);

        BookAppointmentCommand inside = new BookAppointmentCommand(patientId, professionalId, specialtyId,
                day.atTime(11, 0).atZone(clinicZone).toOffsetDateTime(), AppointmentSource.BOT, botUserId, null);
        AppointmentSummary summary = appointmentScheduler.book(inside);

        assertThat(summary.source()).isEqualTo(AppointmentSource.BOT);
        assertThat(summary.status()).isEqualTo(AppointmentStatus.PENDING);
    }

    @Test
    void walkIn_noAppointmentsToday_createsWalkInCheckedIn() {
        WalkInRequest request = new WalkInRequest(patientId, professionalId, specialtyId);
        ResponseEntity<ApiResponse<AppointmentResponse>> response = restTemplate.exchange(
                "/api/v1/appointments/walk-in", HttpMethod.POST, new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AppointmentResponse body = response.getBody().getData();
        assertThat(body.source()).isEqualTo(AppointmentSource.WALK_IN);
        assertThat(body.checkedInAt()).isNotNull();
    }
}
