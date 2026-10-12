package com.bellidental.patients;

import com.bellidental.appointments.api.dto.AppointmentResponse;
import com.bellidental.appointments.api.dto.CreateAppointmentRequest;
import com.bellidental.appointments.api.dto.ScheduleRequest;
import com.bellidental.identity.api.dto.AuthResponse;
import com.bellidental.identity.api.dto.LoginRequest;
import com.bellidental.persistence.entity.Professional;
import com.bellidental.persistence.entity.ProfessionalSpecialty;
import com.bellidental.persistence.repository.ProfessionalRepository;
import com.bellidental.persistence.repository.ProfessionalSpecialtyRepository;
import com.bellidental.persistence.repository.UserRepository;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea 006d: Citas por paciente y observaciones administrativas (adminNotes).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class PatientRecordApiIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private ProfessionalSpecialtyRepository specialtyRepository;

    private final ZoneId clinicZone = ZoneId.of("America/Guayaquil");

    private HttpHeaders headers;
    private UUID professionalId;
    private Long specialtyId;

    @BeforeEach
    void setUp() {
        LoginRequest login = new LoginRequest("secretaria@bellidental.local", "Cambiar.2026!");
        ResponseEntity<ApiResponse<AuthResponse>> loginResp = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(login),
                new ParameterizedTypeReference<>() {}
        );
        headers = new HttpHeaders();
        headers.setBearerAuth(loginResp.getBody().getData().accessToken());

        var user = userRepository.findByUsernameAndDeletedAtIsNull("odontologa").orElseThrow();
        Professional prof = professionalRepository.findByUserId(user.getId()).orElseThrow();
        professionalId = prof.getId();

        ProfessionalSpecialty ps = specialtyRepository.findByProfessionalId(professionalId).stream()
                .filter(ProfessionalSpecialty::isActive)
                .findFirst().orElseThrow();
        specialtyId = ps.getSpecialty().getId();
    }

    @Test
    void adminNotes_createReadClear_roundTrips() {
        String randomSuffix = UUID.randomUUID().toString().substring(0, 6);
        CreatePatientRequest createReq = new CreatePatientRequest(
                DocumentType.PASAPORTE, "P" + randomSuffix, "Ana", "Notas",
                LocalDate.of(1995, 5, 10), "F", null, "0991112233", null, null, null,
                "  Presupuesto ortodoncia $1.200  "
        );

        ResponseEntity<ApiResponse<PatientResponse>> created = restTemplate.exchange(
                "/api/v1/patients",
                HttpMethod.POST,
                new HttpEntity<>(createReq, headers),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().getData().adminNotes()).isEqualTo("Presupuesto ortodoncia $1.200");
        UUID patientId = created.getBody().getData().id();

        ResponseEntity<ApiResponse<PatientResponse>> fetched = restTemplate.exchange(
                "/api/v1/patients/" + patientId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().getData().adminNotes()).isEqualTo("Presupuesto ortodoncia $1.200");

        UpdatePatientRequest updateReq = new UpdatePatientRequest(
                null, null, null, null, null, null, null, null, null, null, null, ""
        );
        ResponseEntity<ApiResponse<PatientResponse>> updated = restTemplate.exchange(
                "/api/v1/patients/" + patientId,
                HttpMethod.PUT,
                new HttpEntity<>(updateReq, headers),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().getData().adminNotes()).isNull();
    }

    @Test
    void getAppointments_byPatientWithoutRange_returnsOnlyThatPatientAscending() {
        LocalDate day1 = LocalDate.now().plusDays(55);
        LocalDate day2 = LocalDate.now().plusDays(56);

        restTemplate.exchange(
                "/api/v1/professionals/" + professionalId + "/schedules",
                HttpMethod.POST,
                new HttpEntity<>(new ScheduleRequest(null, day1, LocalTime.of(8, 0), LocalTime.of(20, 0), true), headers),
                Void.class
        );
        restTemplate.exchange(
                "/api/v1/professionals/" + professionalId + "/schedules",
                HttpMethod.POST,
                new HttpEntity<>(new ScheduleRequest(null, day2, LocalTime.of(8, 0), LocalTime.of(20, 0), true), headers),
                Void.class
        );

        UUID patientAId = createPatient("A" + UUID.randomUUID().toString().substring(0, 6));
        UUID patientBId = createPatient("B" + UUID.randomUUID().toString().substring(0, 6));

        // Dos citas del paciente A en días distintos, la más lejana primero
        OffsetDateTime startA2 = day2.atTime(10, 0).atZone(clinicZone).toOffsetDateTime();
        CreateAppointmentRequest reqA2 = new CreateAppointmentRequest(patientAId, professionalId, specialtyId, startA2, null, null, null);
        restTemplate.exchange("/api/v1/appointments", HttpMethod.POST, new HttpEntity<>(reqA2, headers), Void.class);

        OffsetDateTime startA1 = day1.atTime(10, 0).atZone(clinicZone).toOffsetDateTime();
        CreateAppointmentRequest reqA1 = new CreateAppointmentRequest(patientAId, professionalId, specialtyId, startA1, null, null, null);
        restTemplate.exchange("/api/v1/appointments", HttpMethod.POST, new HttpEntity<>(reqA1, headers), Void.class);

        // Una cita del paciente B
        OffsetDateTime startB1 = day1.atTime(11, 0).atZone(clinicZone).toOffsetDateTime();
        CreateAppointmentRequest reqB1 = new CreateAppointmentRequest(patientBId, professionalId, specialtyId, startB1, null, null, null);
        restTemplate.exchange("/api/v1/appointments", HttpMethod.POST, new HttpEntity<>(reqB1, headers), Void.class);

        ResponseEntity<ApiResponse<List<AppointmentResponse>>> resp = restTemplate.exchange(
                "/api/v1/appointments?patientId=" + patientAId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<AppointmentResponse> appointments = resp.getBody().getData();
        assertThat(appointments).hasSize(2);
        assertThat(appointments.get(0).patientId()).isEqualTo(patientAId);
        assertThat(appointments.get(1).patientId()).isEqualTo(patientAId);
        assertThat(appointments.get(0).startAt()).isBefore(appointments.get(1).startAt());
    }

    @Test
    void getAppointments_invalidFilters_returnBadRequest() {
        UUID randomPatientId = UUID.randomUUID();

        // 1. Sin patientId y sin rango
        ResponseEntity<ApiResponse<Object>> r1 = restTemplate.exchange(
                "/api/v1/appointments",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r1.getBody().getMessage()).isEqualTo("Indica 'from' y 'to'.");

        // 2. Con patientId y solo from
        OffsetDateTime from = LocalDate.now().plusDays(55).atTime(8, 0).atZone(clinicZone).toOffsetDateTime();
        ResponseEntity<ApiResponse<Object>> r2 = restTemplate.exchange(
                "/api/v1/appointments?patientId=" + randomPatientId + "&from=" + from,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r2.getBody().getMessage()).isEqualTo("Indica 'from' y 'to' juntos.");

        // 3. Con patientId y professionalId
        ResponseEntity<ApiResponse<Object>> r3 = restTemplate.exchange(
                "/api/v1/appointments?patientId=" + randomPatientId + "&professionalId=" + professionalId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(r3.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r3.getBody().getMessage()).isEqualTo("Filtra por paciente o por profesional, no por ambos.");
    }

    private UUID createPatient(String docNumber) {
        CreatePatientRequest req = new CreatePatientRequest(
                DocumentType.PASAPORTE, docNumber, "Test", "Patient",
                LocalDate.of(1992, 3, 15), "M", null, "0998877665", null, null, null, null
        );
        ResponseEntity<ApiResponse<PatientResponse>> resp = restTemplate.exchange(
                "/api/v1/patients",
                HttpMethod.POST,
                new HttpEntity<>(req, headers),
                new ParameterizedTypeReference<>() {}
        );
        return resp.getBody().getData().id();
    }
}
