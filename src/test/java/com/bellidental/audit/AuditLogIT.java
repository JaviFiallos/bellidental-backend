package com.bellidental.audit;

import com.bellidental.appointments.api.dto.AppointmentResponse;
import com.bellidental.appointments.api.dto.CreateAppointmentRequest;
import com.bellidental.audit.api.dto.AuditEntryResponse;
import com.bellidental.audit.api.dto.AuditLogEntryResponse;
import com.bellidental.clinical.api.dto.AddDiagnosisRequest;
import com.bellidental.clinical.api.dto.EncounterResponse;
import com.bellidental.clinical.api.dto.Icd10Response;
import com.bellidental.clinical.api.dto.OpenEncounterRequest;
import com.bellidental.identity.api.dto.AuthResponse;
import com.bellidental.identity.api.dto.LoginRequest;
import com.bellidental.patients.CreatePatientRequest;
import com.bellidental.patients.PatientSummary;
import com.bellidental.persistence.repository.ProfessionalRepository;
import com.bellidental.persistence.repository.ProfessionalSpecialtyRepository;
import com.bellidental.persistence.repository.UserRepository;
import com.bellidental.shared.enums.DiagnosisCertainty;
import com.bellidental.shared.enums.DocumentType;
import com.bellidental.shared.response.ApiResponse;
import com.bellidental.support.TestcontainersConfiguration;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AuditLogIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private ProfessionalSpecialtyRepository specialtyRepository;

    @Autowired
    private UserRepository userRepository;

    private static final java.util.concurrent.atomic.AtomicInteger DAY_COUNTER = new java.util.concurrent.atomic.AtomicInteger(121);

    private HttpHeaders directorHeaders;
    private HttpHeaders secretariaHeaders;
    private HttpHeaders odontologaHeaders;
    private UUID patientId;
    private String patientFullName;
    private UUID encounterId;

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PageResponse<T>(List<T> content, long totalElements) {}

    @BeforeEach
    void setUp() {
        directorHeaders = loginAs("director@bellidental.local");
        secretariaHeaders = loginAs("secretaria@bellidental.local");
        odontologaHeaders = loginAs("odontologa@bellidental.local");

        String randomSuffix = UUID.randomUUID().toString().substring(0, 5);
        String firstName = "Audit" + randomSuffix;
        String lastName = "Patient";
        patientFullName = firstName + " " + lastName;

        CreatePatientRequest patientRequest = new CreatePatientRequest(
                DocumentType.PASAPORTE, "A99" + randomSuffix, firstName, lastName,
                LocalDate.of(1990, 1, 1), "M", null, "0999999995", null, null, null, null
        );
        ResponseEntity<ApiResponse<PatientSummary>> patientResponse = exchange(
                HttpMethod.POST, "/api/v1/patients", patientRequest, secretariaHeaders,
                new ParameterizedTypeReference<>() {});
        patientId = patientResponse.getBody().getData().id();

        var user = userRepository.findByUsernameAndDeletedAtIsNull("odontologa").orElseThrow();
        UUID professionalId = professionalRepository.findByUserId(user.getId()).orElseThrow().getId();
        Long specialtyId = specialtyRepository.findByProfessionalId(professionalId).stream()
                .filter(s -> s.isActive())
                .findFirst()
                .orElseThrow()
                .getSpecialty().getId();

        LocalDate appointmentDay = LocalDate.now().plusDays(DAY_COUNTER.getAndIncrement());
        OffsetDateTime startAt = appointmentDay.atTime(10, 0)
                .atZone(ZoneId.of("America/Guayaquil"))
                .toOffsetDateTime();
        CreateAppointmentRequest appointmentRequest = new CreateAppointmentRequest(
                patientId, professionalId, specialtyId, startAt, null, "Auditoría", null
        );
        ResponseEntity<ApiResponse<AppointmentResponse>> appointmentResponse = exchange(
                HttpMethod.POST, "/api/v1/appointments", appointmentRequest, odontologaHeaders,
                new ParameterizedTypeReference<>() {});
        UUID appointmentId = appointmentResponse.getBody().getData().id();

        exchange(HttpMethod.POST, "/api/v1/appointments/" + appointmentId + "/check-in", null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<Void>>() {});

        ResponseEntity<ApiResponse<EncounterResponse>> openResponse = exchange(
                HttpMethod.POST, "/api/v1/appointments/" + appointmentId + "/encounter",
                new OpenEncounterRequest("Evaluación"), odontologaHeaders, new ParameterizedTypeReference<>() {});
        encounterId = openResponse.getBody().getData().id();

        Long icd10Id = findIcd10Id("K02.1");
        exchange(HttpMethod.POST, "/api/v1/encounters/" + encounterId + "/diagnoses",
                new AddDiagnosisRequest(icd10Id, DiagnosisCertainty.DEFINITIVE, null), odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<Object>>() {});
    }

    @Test
    void listAudit_byPatient_returnsEntriesOfPatientEncounterAndDiagnosis() {
        ResponseEntity<ApiResponse<PageResponse<AuditLogEntryResponse>>> response = exchange(
                HttpMethod.GET, "/api/v1/audit?patientId=" + patientId, null, directorHeaders,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PageResponse<AuditLogEntryResponse> page = response.getBody().getData();
        assertThat(page.totalElements()).isGreaterThanOrEqualTo(3);

        List<AuditLogEntryResponse> content = page.content();
        assertThat(content).allMatch(e -> patientId.equals(e.patientId()) && patientFullName.equals(e.patientName()));

        assertThat(content).anyMatch(e -> "patients".equals(e.entity()) && "ADD".equals(e.type()));
        assertThat(content).anyMatch(e -> "encounters".equals(e.entity()));
        assertThat(content).anyMatch(e -> "encounter-diagnoses".equals(e.entity()));
    }

    @Test
    void encounterFullHistory_includesDiagnosis() {
        ResponseEntity<ApiResponse<List<AuditEntryResponse>>> response = exchange(
                HttpMethod.GET, "/api/v1/audit/encounters/" + encounterId + "/full", null, directorHeaders,
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<AuditEntryResponse> entries = response.getBody().getData();
        assertThat(entries).isNotEmpty();

        assertThat(entries).anyMatch(e -> "encounters".equals(e.entity()));
        assertThat(entries).anyMatch(e -> "encounter-diagnoses".equals(e.entity()));

        assertThat(entries).isSortedAccordingTo(Comparator.comparing(AuditEntryResponse::at));
    }

    @Test
    void listAudit_invalidOrForbidden_returnsErrors() {
        ResponseEntity<ApiResponse<PageResponse<AuditLogEntryResponse>>> forbidden = exchange(
                HttpMethod.GET, "/api/v1/audit", null, secretariaHeaders,
                new ParameterizedTypeReference<>() {});
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<ApiResponse<Void>> invalidDates = exchange(
                HttpMethod.GET, "/api/v1/audit?from=2026-10-25&to=2026-10-20", null, directorHeaders,
                new ParameterizedTypeReference<>() {});
        assertThat(invalidDates.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private Long findIcd10Id(String code) {
        ResponseEntity<ApiResponse<List<Icd10Response>>> response = exchange(
                HttpMethod.GET, "/api/v1/icd10?query=" + code, null, odontologaHeaders,
                new ParameterizedTypeReference<>() {});
        return response.getBody().getData().stream()
                .filter(icd10 -> code.equals(icd10.code()))
                .findFirst()
                .map(Icd10Response::id)
                .orElseThrow();
    }

    private <T> ResponseEntity<ApiResponse<T>> exchange(HttpMethod method, String url, Object body, HttpHeaders headers,
                                                        ParameterizedTypeReference<ApiResponse<T>> type) {
        return restTemplate.exchange(url, method, new HttpEntity<>(body, headers), type);
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
