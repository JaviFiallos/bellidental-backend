package com.bellidental.appointments;

import com.bellidental.appointments.api.dto.AppointmentResponse;
import com.bellidental.appointments.api.dto.CreateAppointmentRequest;
import com.bellidental.appointments.api.dto.RescheduleAppointmentRequest;
import com.bellidental.appointments.api.dto.ScheduleRequest;
import com.bellidental.identity.api.dto.AuthResponse;
import com.bellidental.identity.api.dto.LoginRequest;
import com.bellidental.patients.CreatePatientRequest;
import com.bellidental.patients.PatientSummary;
import com.bellidental.persistence.entity.Professional;
import com.bellidental.persistence.entity.ProfessionalSpecialty;
import com.bellidental.persistence.repository.ProfessionalRepository;
import com.bellidental.persistence.repository.ProfessionalSpecialtyRepository;
import com.bellidental.persistence.repository.UserRepository;
import com.bellidental.shared.enums.AppointmentStatus;
import com.bellidental.shared.enums.DocumentType;
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

    private HttpHeaders headers;
    private UUID patientId;
    private UUID professionalId;
    private Long specialtyId;
    private LocalDate futureDate;
    private ZoneId clinicZone = ZoneId.of("America/Guayaquil");

    @BeforeEach
    void setUp() {
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
}
