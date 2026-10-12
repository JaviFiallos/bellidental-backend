package com.bellidental.appointments;

import com.bellidental.appointments.api.dto.AppointmentResponse;
import com.bellidental.appointments.api.dto.CreateAppointmentRequest;
import com.bellidental.clinical.api.dto.EncounterResponse;
import com.bellidental.clinical.api.dto.OpenEncounterRequest;
import com.bellidental.clinical.api.dto.VoidEncounterRequest;
import com.bellidental.identity.api.dto.AuthResponse;
import com.bellidental.identity.api.dto.LoginRequest;
import com.bellidental.patients.CreatePatientRequest;
import com.bellidental.patients.PatientSummary;
import com.bellidental.persistence.repository.ProfessionalRepository;
import com.bellidental.persistence.repository.ProfessionalSpecialtyRepository;
import com.bellidental.persistence.repository.UserRepository;
import com.bellidental.shared.enums.DocumentType;
import com.bellidental.shared.enums.EncounterStatus;
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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AppointmentEncounterLinkIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private ProfessionalSpecialtyRepository specialtyRepository;

    @Autowired
    private UserRepository userRepository;

    private HttpHeaders secretariaHeaders;
    private HttpHeaders odontologaHeaders;
    private UUID patientId;
    private UUID professionalId;
    private Long specialtyId;

    @BeforeEach
    void setUp() {
        secretariaHeaders = loginAs("secretaria@bellidental.local");
        odontologaHeaders = loginAs("odontologa@bellidental.local");

        String randomSuffix = UUID.randomUUID().toString().substring(0, 5);
        CreatePatientRequest patientRequest = new CreatePatientRequest(
                DocumentType.PASAPORTE, "B88200" + randomSuffix, "Test", "EncounterLink",
                LocalDate.of(1990, 1, 1), "F", null, "0999999998", null, null, null, null
        );
        ResponseEntity<ApiResponse<PatientSummary>> patientResponse = restTemplate.exchange(
                "/api/v1/patients",
                HttpMethod.POST,
                new HttpEntity<>(patientRequest, secretariaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        patientId = patientResponse.getBody().getData().id();

        var user = userRepository.findByUsernameAndDeletedAtIsNull("odontologa").orElseThrow();
        professionalId = professionalRepository.findByUserId(user.getId()).orElseThrow().getId();
        specialtyId = specialtyRepository.findByProfessionalId(professionalId).stream()
                .filter(s -> s.isActive())
                .findFirst()
                .orElseThrow()
                .getSpecialty().getId();
    }

    private UUID createAppointment(OffsetDateTime startAt) {
        CreateAppointmentRequest request = new CreateAppointmentRequest(
                patientId, professionalId, specialtyId, startAt, null, "Consulta general", null
        );
        ResponseEntity<ApiResponse<AppointmentResponse>> response = restTemplate.exchange(
                "/api/v1/appointments",
                HttpMethod.POST,
                new HttpEntity<>(request, odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().getData().id();
    }

    @Test
    void getAppointments_withAndWithoutEncounter_reportsLink() {
        LocalDate day = LocalDate.now().plusDays(25);
        OffsetDateTime start1 = day.atTime(9, 0).atZone(ZoneId.of("America/Guayaquil")).toOffsetDateTime();
        OffsetDateTime start2 = day.atTime(10, 0).atZone(ZoneId.of("America/Guayaquil")).toOffsetDateTime();

        UUID appointment1Id = createAppointment(start1);
        UUID appointment2Id = createAppointment(start2);

        // Check-in de la primera y apertura de la atención como ese odontólogo
        restTemplate.exchange(
                "/api/v1/appointments/" + appointment1Id + "/check-in",
                HttpMethod.POST,
                new HttpEntity<>(odontologaHeaders),
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
        );

        OpenEncounterRequest openRequest = new OpenEncounterRequest("Dolor en molar");
        ResponseEntity<ApiResponse<EncounterResponse>> openResponse = restTemplate.exchange(
                "/api/v1/appointments/" + appointment1Id + "/encounter",
                HttpMethod.POST,
                new HttpEntity<>(openRequest, odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID encounterId = openResponse.getBody().getData().id();

        OffsetDateTime from = day.atStartOfDay(ZoneId.of("America/Guayaquil")).toOffsetDateTime();
        OffsetDateTime to = from.plusDays(1);

        ResponseEntity<ApiResponse<List<AppointmentResponse>>> listResponse = restTemplate.exchange(
                "/api/v1/appointments?professionalId={professionalId}&from={from}&to={to}",
                HttpMethod.GET,
                new HttpEntity<>(odontologaHeaders),
                new ParameterizedTypeReference<>() {},
                professionalId, from, to
        );

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<AppointmentResponse> list = listResponse.getBody().getData();

        AppointmentResponse appt1 = list.stream().filter(a -> a.id().equals(appointment1Id)).findFirst().orElseThrow();
        AppointmentResponse appt2 = list.stream().filter(a -> a.id().equals(appointment2Id)).findFirst().orElseThrow();

        assertThat(appt1.encounterId()).isEqualTo(encounterId);
        assertThat(appt1.encounterStatus()).isEqualTo(EncounterStatus.OPEN);
        assertThat(appt1.encounterVoided()).isFalse();

        assertThat(appt2.encounterId()).isNull();
        assertThat(appt2.encounterStatus()).isNull();
        assertThat(appt2.encounterVoided()).isFalse();
    }

    @Test
    void getAppointment_afterVoidingEncounter_reportsVoided() {
        LocalDate day = LocalDate.now().plusDays(26);
        OffsetDateTime start = day.atTime(9, 0).atZone(ZoneId.of("America/Guayaquil")).toOffsetDateTime();

        UUID appointmentId = createAppointment(start);

        restTemplate.exchange(
                "/api/v1/appointments/" + appointmentId + "/check-in",
                HttpMethod.POST,
                new HttpEntity<>(odontologaHeaders),
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
        );

        OpenEncounterRequest openRequest = new OpenEncounterRequest("Revisión");
        ResponseEntity<ApiResponse<EncounterResponse>> openResponse = restTemplate.exchange(
                "/api/v1/appointments/" + appointmentId + "/encounter",
                HttpMethod.POST,
                new HttpEntity<>(openRequest, odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID encounterId = openResponse.getBody().getData().id();

        VoidEncounterRequest voidRequest = new VoidEncounterRequest("Paciente se retiró antes del procedimiento");
        ResponseEntity<ApiResponse<EncounterResponse>> voidResponse = restTemplate.exchange(
                "/api/v1/encounters/" + encounterId + "/void",
                HttpMethod.POST,
                new HttpEntity<>(voidRequest, odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(voidResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiResponse<AppointmentResponse>> getResponse = restTemplate.exchange(
                "/api/v1/appointments/" + appointmentId,
                HttpMethod.GET,
                new HttpEntity<>(odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        AppointmentResponse appt = getResponse.getBody().getData();

        assertThat(appt.encounterId()).isEqualTo(encounterId);
        assertThat(appt.encounterVoided()).isTrue();
    }

    private HttpHeaders loginAs(String email) {
        ResponseEntity<ApiResponse<AuthResponse>> loginResponse = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(new LoginRequest(email, "Cambiar.2026!")),
                new ParameterizedTypeReference<>() {}
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loginResponse.getBody().getData().accessToken());
        return headers;
    }
}
