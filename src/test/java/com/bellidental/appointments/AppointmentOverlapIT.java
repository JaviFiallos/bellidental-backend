package com.bellidental.appointments;

import com.bellidental.appointments.api.dto.AppointmentResponse;
import com.bellidental.appointments.api.dto.CreateAppointmentRequest;
import com.bellidental.appointments.api.dto.RescheduleAppointmentRequest;
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
import com.bellidental.shared.response.ApiResponse;
import com.bellidental.support.MutableClock;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AppointmentOverlapIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private ProfessionalSpecialtyRepository specialtyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MutableClock clock;

    private HttpHeaders headers;
    private UUID patientId;
    private UUID professionalId;
    private Long specialtyId;
    private LocalDate futureDate;
    private ZoneId clinicZone = ZoneId.of("America/Guayaquil");

    @BeforeEach
    void setUp() {
        clock.reset();
        // 1. Login as secretaria
        LoginRequest login = new LoginRequest("secretaria@bellidental.local", "Cambiar.2026!");
        ResponseEntity<ApiResponse<AuthResponse>> loginResponse = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(login),
                new ParameterizedTypeReference<>() {}
        );
        String token = loginResponse.getBody().getData().accessToken();
        headers = new HttpHeaders();
        headers.setBearerAuth(token);

        // 2. Create patient via API
        String randomSuffix = UUID.randomUUID().toString().substring(0, 5);
        CreatePatientRequest patientReq = new CreatePatientRequest(
                DocumentType.PASAPORTE, "A17100" + randomSuffix, "Test", "Patient",
                LocalDate.of(1990, 1, 1), "M", null, "0999999999", null, null, null, null
        );
        ResponseEntity<ApiResponse<PatientSummary>> patientResp = restTemplate.exchange(
                "/api/v1/patients",
                HttpMethod.POST,
                new HttpEntity<>(patientReq, headers),
                new ParameterizedTypeReference<>() {}
        );
        patientId = patientResp.getBody().getData().id();

        // 3. Get professional and specialty
        var user = userRepository.findByUsernameAndDeletedAtIsNull("odontologa").orElseThrow();
        Professional prof = professionalRepository.findByUserId(user.getId()).orElseThrow();
        professionalId = prof.getId();
        
        ProfessionalSpecialty ps = specialtyRepository.findByProfessionalId(professionalId).stream()
                .filter(ProfessionalSpecialty::isActive)
                .findFirst().orElseThrow();
        specialtyId = ps.getSpecialty().getId();

        // 4. Create schedule
        futureDate = LocalDate.now().plusDays(7);
        ScheduleRequest scheduleReq = new ScheduleRequest(
                null, futureDate, LocalTime.of(8, 0), LocalTime.of(20, 0), true
        );
        restTemplate.exchange(
                "/api/v1/professionals/" + professionalId + "/schedules",
                HttpMethod.POST,
                new HttpEntity<>(scheduleReq, headers),
                Void.class
        );
    }

    @Test
    void create_overlappingAppointment_returnsConflictUntilCancelled() {
        // Cita A 10:00
        OffsetDateTime startA = futureDate.atTime(10, 0).atZone(clinicZone).toOffsetDateTime();
        CreateAppointmentRequest reqA = new CreateAppointmentRequest(patientId, professionalId, specialtyId, startA, null, null, null);
        
        ResponseEntity<ApiResponse<AppointmentResponse>> resA = restTemplate.exchange(
                "/api/v1/appointments",
                HttpMethod.POST,
                new HttpEntity<>(reqA, headers),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resA.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID citaAId = resA.getBody().getData().id();

        // Cita B 10:15 (Overlap, default duration is 30 mins)
        OffsetDateTime startB = futureDate.atTime(10, 15).atZone(clinicZone).toOffsetDateTime();
        CreateAppointmentRequest reqB = new CreateAppointmentRequest(patientId, professionalId, specialtyId, startB, null, null, null);
        
        ResponseEntity<ApiResponse<Object>> resBError = restTemplate.exchange(
                "/api/v1/appointments",
                HttpMethod.POST,
                new HttpEntity<>(reqB, headers),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resBError.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Cancelar A
        ResponseEntity<Void> resCancelA = restTemplate.exchange(
                "/api/v1/appointments/" + citaAId + "/cancel",
                HttpMethod.POST,
                new HttpEntity<>(null, headers),
                Void.class
        );
        assertThat(resCancelA.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Crear B de nuevo -> 201
        ResponseEntity<ApiResponse<AppointmentResponse>> resB = restTemplate.exchange(
                "/api/v1/appointments",
                HttpMethod.POST,
                new HttpEntity<>(reqB, headers),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resB.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void reschedule_toOverlappingOwnSlot_marksOriginalRescheduled() {
        // Cita C 15:00
        OffsetDateTime startC = futureDate.atTime(15, 0).atZone(clinicZone).toOffsetDateTime();
        CreateAppointmentRequest reqC = new CreateAppointmentRequest(patientId, professionalId, specialtyId, startC, null, null, null);
        
        ResponseEntity<ApiResponse<AppointmentResponse>> resC = restTemplate.exchange(
                "/api/v1/appointments",
                HttpMethod.POST,
                new HttpEntity<>(reqC, headers),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resC.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID citaCId = resC.getBody().getData().id();

        // Reprogramar C a 15:15
        OffsetDateTime startCRescheduled = futureDate.atTime(15, 15).atZone(clinicZone).toOffsetDateTime();
        RescheduleAppointmentRequest rescheduleReq = new RescheduleAppointmentRequest(startCRescheduled, null, null, null);
        
        ResponseEntity<ApiResponse<AppointmentResponse>> resReschedule = restTemplate.exchange(
                "/api/v1/appointments/" + citaCId + "/reschedule",
                HttpMethod.POST,
                new HttpEntity<>(rescheduleReq, headers),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resReschedule.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // GET C muestra RESCHEDULED
        ResponseEntity<ApiResponse<AppointmentResponse>> getC = restTemplate.exchange(
                "/api/v1/appointments/" + citaCId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(getC.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getC.getBody().getData().status()).isEqualTo(AppointmentStatus.RESCHEDULED);
    }

    @Test
    void walkIn_withChosenHours_createsWalkInAndChecksIn() {
        LocalDate today = LocalDate.now(clinicZone);
        OffsetDateTime fixedNow = today.atTime(10, 7).atZone(clinicZone).toOffsetDateTime();
        clock.fix(fixedNow.toInstant());

        OffsetDateTime startAt = today.atTime(11, 0).atZone(clinicZone).toOffsetDateTime();
        OffsetDateTime endAt = today.atTime(11, 30).atZone(clinicZone).toOffsetDateTime();
        WalkInRequest req = new WalkInRequest(patientId, professionalId, specialtyId, startAt, endAt);

        ResponseEntity<ApiResponse<AppointmentResponse>> response = restTemplate.exchange(
                "/api/v1/appointments/walk-in",
                HttpMethod.POST,
                new HttpEntity<>(req, headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AppointmentResponse data = response.getBody().getData();
        assertThat(data.startAt()).isEqualTo(startAt);
        assertThat(data.endAt()).isEqualTo(endAt);
        assertThat(data.source()).isEqualTo(AppointmentSource.WALK_IN);
        assertThat(data.checkedInAt()).isNotNull();
    }

    @Test
    void walkIn_overlappingExistingAppointment_returnsConflict() {
        LocalDate today = LocalDate.now(clinicZone);
        OffsetDateTime fixedNow = today.atTime(10, 0).atZone(clinicZone).toOffsetDateTime();
        clock.fix(fixedNow.toInstant());

        OffsetDateTime startAt = today.atTime(12, 0).atZone(clinicZone).toOffsetDateTime();
        OffsetDateTime endAt = today.atTime(12, 30).atZone(clinicZone).toOffsetDateTime();

        WalkInRequest req1 = new WalkInRequest(patientId, professionalId, specialtyId, startAt, endAt);
        ResponseEntity<ApiResponse<AppointmentResponse>> res1 = restTemplate.exchange(
                "/api/v1/appointments/walk-in",
                HttpMethod.POST,
                new HttpEntity<>(req1, headers),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        WalkInRequest req2 = new WalkInRequest(patientId, professionalId, specialtyId, startAt, endAt);
        ResponseEntity<ApiResponse<Object>> res2 = restTemplate.exchange(
                "/api/v1/appointments/walk-in",
                HttpMethod.POST,
                new HttpEntity<>(req2, headers),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void walkIn_pastHoursOrYesterday_returnsBadRequest() {
        LocalDate today = LocalDate.now(clinicZone);
        OffsetDateTime fixedNow = today.atTime(10, 7).atZone(clinicZone).toOffsetDateTime();
        clock.fix(fixedNow.toInstant());

        // Ayer
        OffsetDateTime yesterdayStart = today.minusDays(1).atTime(10, 0).atZone(clinicZone).toOffsetDateTime();
        OffsetDateTime yesterdayEnd = today.minusDays(1).atTime(10, 30).atZone(clinicZone).toOffsetDateTime();
        WalkInRequest reqYesterday = new WalkInRequest(patientId, professionalId, specialtyId, yesterdayStart, yesterdayEnd);
        ResponseEntity<ApiResponse<Void>> resYesterday = restTemplate.exchange(
                "/api/v1/appointments/walk-in",
                HttpMethod.POST,
                new HttpEntity<>(reqYesterday, headers),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resYesterday.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resYesterday.getBody().getMessage()).isEqualTo("La atención sin cita es solo para hoy.");

        // Hace 1 hora (09:00, antes del bloque actual de 10:00)
        OffsetDateTime oneHourAgoStart = today.atTime(9, 0).atZone(clinicZone).toOffsetDateTime();
        OffsetDateTime oneHourAgoEnd = today.atTime(9, 30).atZone(clinicZone).toOffsetDateTime();
        WalkInRequest reqPast = new WalkInRequest(patientId, professionalId, specialtyId, oneHourAgoStart, oneHourAgoEnd);
        ResponseEntity<ApiResponse<Void>> resPast = restTemplate.exchange(
                "/api/v1/appointments/walk-in",
                HttpMethod.POST,
                new HttpEntity<>(reqPast, headers),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resPast.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resPast.getBody().getMessage()).isEqualTo("La hora ya pasó.");
    }
}
