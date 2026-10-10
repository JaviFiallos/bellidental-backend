package com.bellidental.clinical;

import com.bellidental.appointments.api.dto.AppointmentResponse;
import com.bellidental.appointments.api.dto.CreateAppointmentRequest;
import com.bellidental.clinical.api.dto.AddDiagnosisRequest;
import com.bellidental.clinical.api.dto.DiagnosisResponse;
import com.bellidental.clinical.api.dto.EncounterResponse;
import com.bellidental.clinical.api.dto.Icd10Response;
import com.bellidental.clinical.api.dto.OpenEncounterRequest;
import com.bellidental.clinicalrecords.ClinicalRecordResponse;
import com.bellidental.identity.api.dto.AuthResponse;
import com.bellidental.identity.api.dto.LoginRequest;
import com.bellidental.patients.CreatePatientRequest;
import com.bellidental.patients.PatientSummary;
import com.bellidental.persistence.entity.Professional;
import com.bellidental.persistence.repository.ProfessionalRepository;
import com.bellidental.persistence.repository.ProfessionalSpecialtyRepository;
import com.bellidental.persistence.repository.UserRepository;
import com.bellidental.shared.enums.AppointmentStatus;
import com.bellidental.shared.enums.DiagnosisCertainty;
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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class EncounterFlowIT {

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

        // Secretaria crea un paciente
        String randomSuffix = UUID.randomUUID().toString().substring(0, 5);
        CreatePatientRequest patientRequest = new CreatePatientRequest(
                DocumentType.PASAPORTE, "B88100" + randomSuffix, "Test", "Encounter",
                LocalDate.of(1990, 1, 1), "F", null, "0999999998", null, null, null
        );
        ResponseEntity<ApiResponse<PatientSummary>> patientResponse = restTemplate.exchange(
                "/api/v1/patients",
                HttpMethod.POST,
                new HttpEntity<>(patientRequest, secretariaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        patientId = patientResponse.getBody().getData().id();

        // Odontóloga: profesional y especialidad activa de la semilla
        var user = userRepository.findByUsernameAndDeletedAtIsNull("odontologa").orElseThrow();
        professionalId = professionalRepository.findByUserId(user.getId()).orElseThrow().getId();
        specialtyId = specialtyRepository.findByProfessionalId(professionalId).stream()
                .filter(s -> s.isActive())
                .findFirst()
                .orElseThrow()
                .getSpecialty().getId();
    }

    /**
     * Cada test crea su propia cita con check-in: la base es compartida entre tests y dos citas
     * del mismo profesional en la misma franja chocan (EXCLUDE de appointments).
     */
    private UUID createCheckedInAppointment(LocalDate day) {
        OffsetDateTime startAt = day.atTime(10, 0)
                .atZone(ZoneId.of("America/Guayaquil"))
                .toOffsetDateTime();
        CreateAppointmentRequest appointmentRequest = new CreateAppointmentRequest(
                patientId, professionalId, specialtyId, startAt, null, "Dolor dental", null
        );
        ResponseEntity<ApiResponse<AppointmentResponse>> appointmentResponse = restTemplate.exchange(
                "/api/v1/appointments",
                HttpMethod.POST,
                new HttpEntity<>(appointmentRequest, odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(appointmentResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID appointmentId = appointmentResponse.getBody().getData().id();

        restTemplate.exchange(
                "/api/v1/appointments/" + appointmentId + "/check-in",
                HttpMethod.POST,
                new HttpEntity<>(odontologaHeaders),
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
        );
        return appointmentId;
    }

    @Test
    void encounterFlow_openCompleteDiagnosis_succeeds() {
        UUID appointmentId = createCheckedInAppointment(LocalDate.now().plusDays(1));

        // 1. Odontóloga abre atención → 201
        OpenEncounterRequest openRequest = new OpenEncounterRequest("Dolor en molar superior");
        ResponseEntity<ApiResponse<EncounterResponse>> openResponse = restTemplate.exchange(
                "/api/v1/appointments/" + appointmentId + "/encounter",
                HttpMethod.POST,
                new HttpEntity<>(openRequest, odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID encounterId = openResponse.getBody().getData().id();

        // 2. Completar sin diagnóstico → 409
        ResponseEntity<ApiResponse<EncounterResponse>> completeEarlyResponse = restTemplate.exchange(
                "/api/v1/encounters/" + encounterId + "/complete",
                HttpMethod.POST,
                new HttpEntity<>(odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(completeEarlyResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // 3. Buscar K02.1 por código
        ResponseEntity<ApiResponse<List<Icd10Response>>> icd10Response = restTemplate.exchange(
                "/api/v1/icd10?query=K02.1",
                HttpMethod.GET,
                new HttpEntity<>(odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(icd10Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Long icd10Id = icd10Response.getBody().getData().stream()
                .filter(c -> "K02.1".equals(c.code()))
                .findFirst()
                .map(Icd10Response::id)
                .orElseThrow();

        // 4. Añadir diagnóstico K02.1 → 201
        AddDiagnosisRequest diagnosisRequest = new AddDiagnosisRequest(icd10Id, DiagnosisCertainty.DEFINITIVE, null);
        ResponseEntity<ApiResponse<DiagnosisResponse>> diagnosisResponse = restTemplate.exchange(
                "/api/v1/encounters/" + encounterId + "/diagnoses",
                HttpMethod.POST,
                new HttpEntity<>(diagnosisRequest, odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(diagnosisResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // 5. Completar atención → 200
        ResponseEntity<ApiResponse<EncounterResponse>> completeResponse = restTemplate.exchange(
                "/api/v1/encounters/" + encounterId + "/complete",
                HttpMethod.POST,
                new HttpEntity<>(odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(completeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 6. GET /appointments/{id} → ATTENDED
        ResponseEntity<ApiResponse<AppointmentResponse>> appointmentResp = restTemplate.exchange(
                "/api/v1/appointments/" + appointmentId,
                HttpMethod.GET,
                new HttpEntity<>(odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(appointmentResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(appointmentResp.getBody().getData().status()).isEqualTo(AppointmentStatus.ATTENDED);

        // 7. GET /patients/{id}/clinical-record → 200 (historia abierta automáticamente)
        ResponseEntity<ApiResponse<ClinicalRecordResponse>> recordResponse = restTemplate.exchange(
                "/api/v1/patients/" + patientId + "/clinical-record",
                HttpMethod.GET,
                new HttpEntity<>(odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(recordResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void openEncounter_asSecretaria_returnsForbidden() {
        UUID appointmentId = createCheckedInAppointment(LocalDate.now().plusDays(2));

        // La secretaria intenta abrir la atención → 403
        OpenEncounterRequest openRequest = new OpenEncounterRequest("Intento no autorizado");
        ResponseEntity<ApiResponse<EncounterResponse>> response = restTemplate.exchange(
                "/api/v1/appointments/" + appointmentId + "/encounter",
                HttpMethod.POST,
                new HttpEntity<>(openRequest, secretariaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
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
