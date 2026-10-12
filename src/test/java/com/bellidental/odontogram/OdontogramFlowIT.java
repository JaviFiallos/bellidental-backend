package com.bellidental.odontogram;

import com.bellidental.appointments.api.dto.AppointmentResponse;
import com.bellidental.appointments.api.dto.CreateAppointmentRequest;
import com.bellidental.clinical.api.dto.AddDiagnosisRequest;
import com.bellidental.clinical.api.dto.DiagnosisResponse;
import com.bellidental.clinical.api.dto.EncounterResponse;
import com.bellidental.clinical.api.dto.Icd10Response;
import com.bellidental.clinical.api.dto.OpenEncounterRequest;
import com.bellidental.identity.api.dto.AuthResponse;
import com.bellidental.identity.api.dto.LoginRequest;
import com.bellidental.odontogram.api.dto.CatalogResponse;
import com.bellidental.odontogram.api.dto.CreateFindingRequest;
import com.bellidental.odontogram.api.dto.FindingResponse;
import com.bellidental.odontogram.api.dto.OdontogramResponse;
import com.bellidental.patients.CreatePatientRequest;
import com.bellidental.patients.PatientSummary;
import com.bellidental.persistence.repository.ProfessionalRepository;
import com.bellidental.persistence.repository.ProfessionalSpecialtyRepository;
import com.bellidental.persistence.repository.UserRepository;
import com.bellidental.shared.enums.DiagnosisCertainty;
import com.bellidental.shared.enums.DocumentType;
import com.bellidental.shared.enums.FindingStatus;
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
class OdontogramFlowIT {

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
    private UUID encounterId;

    private static int startHour = 8;

    @BeforeEach
    void setUp() {
        secretariaHeaders = loginAs("secretaria@bellidental.local");
        odontologaHeaders = loginAs("odontologa@bellidental.local");

        String randomSuffix = UUID.randomUUID().toString().substring(0, 5);
        CreatePatientRequest patientRequest = new CreatePatientRequest(
                DocumentType.PASAPORTE, "B99100" + randomSuffix, "Odonto", "Paciente",
                LocalDate.of(1990, 1, 1), "F", null, "0999999999", null, null, null, null
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

        OffsetDateTime startAt = LocalDate.now().plusDays(20).atTime(startHour++, 0)
                .atZone(ZoneId.of("America/Guayaquil"))
                .toOffsetDateTime();
        CreateAppointmentRequest appointmentRequest = new CreateAppointmentRequest(
                patientId, professionalId, specialtyId, startAt, null, "Revisión general", null
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

        OpenEncounterRequest openRequest = new OpenEncounterRequest("Revisión");
        ResponseEntity<ApiResponse<EncounterResponse>> openResponse = restTemplate.exchange(
                "/api/v1/appointments/" + appointmentId + "/encounter",
                HttpMethod.POST,
                new HttpEntity<>(openRequest, odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        encounterId = openResponse.getBody().getData().id();
    }

    @Test
    void catalogSucceeds() {
        ResponseEntity<ApiResponse<CatalogResponse>> response = restTemplate.exchange(
                "/api/v1/odontogram/catalog",
                HttpMethod.GET,
                new HttpEntity<>(odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        CatalogResponse catalog = response.getBody().getData();
        assertThat(catalog.teeth()).hasSize(52);
        assertThat(catalog.surfaces()).hasSize(5);
        assertThat(catalog.conditions()).hasSize(14);
        assertThat(catalog.conditions()).allMatch(c -> c.symbols() != null && !c.symbols().isEmpty());
    }

    @Test
    void correctionAfterCompletionSucceeds() {
        ResponseEntity<ApiResponse<CatalogResponse>> catResp = restTemplate.exchange(
                "/api/v1/odontogram/catalog",
                HttpMethod.GET,
                new HttpEntity<>(odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        Long cariesId = catResp.getBody().getData().conditions().stream().filter(c -> "CARIES".equals(c.code())).findFirst().orElseThrow().id();
        Long fillingId = catResp.getBody().getData().conditions().stream().filter(c -> "FILLING".equals(c.code())).findFirst().orElseThrow().id();

        CreateFindingRequest cariesO = new CreateFindingRequest((short) 16, null, "O", cariesId, FindingStatus.PENDING, null, null);
        ResponseEntity<ApiResponse<FindingResponse>> resO = restTemplate.exchange(
                "/api/v1/encounters/" + encounterId + "/findings",
                HttpMethod.POST,
                new HttpEntity<>(cariesO, odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resO.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        CreateFindingRequest cariesM = new CreateFindingRequest((short) 16, null, "M", cariesId, FindingStatus.PENDING, null, null);
        ResponseEntity<ApiResponse<FindingResponse>> resM = restTemplate.exchange(
                "/api/v1/encounters/" + encounterId + "/findings",
                HttpMethod.POST,
                new HttpEntity<>(cariesM, odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resM.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID finding16MId = resM.getBody().getData().id();

        ResponseEntity<ApiResponse<List<Icd10Response>>> icd10Response = restTemplate.exchange(
                "/api/v1/icd10?query=K02", HttpMethod.GET, new HttpEntity<>(odontologaHeaders), new ParameterizedTypeReference<>() {}
        );
        Long icd10Id = icd10Response.getBody().getData().get(0).id();
        AddDiagnosisRequest diagnosisRequest = new AddDiagnosisRequest(icd10Id, DiagnosisCertainty.DEFINITIVE, null);
        restTemplate.exchange("/api/v1/encounters/" + encounterId + "/diagnoses", HttpMethod.POST, new HttpEntity<>(diagnosisRequest, odontologaHeaders), new ParameterizedTypeReference<>() {});

        ResponseEntity<ApiResponse<EncounterResponse>> completeResponse = restTemplate.exchange(
                "/api/v1/encounters/" + encounterId + "/complete",
                HttpMethod.POST,
                new HttpEntity<>(odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(completeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(completeResponse.getBody().getData().status().name()).isEqualTo("COMPLETED");

        ResponseEntity<ApiResponse<Void>> voidRes = restTemplate.exchange(
                "/api/v1/findings/" + finding16MId + "/void",
                HttpMethod.POST,
                new HttpEntity<>(odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(voidRes.getStatusCode()).isEqualTo(HttpStatus.OK);

        CreateFindingRequest fillingM = new CreateFindingRequest((short) 16, null, "M", fillingId, FindingStatus.DONE, null, null);
        ResponseEntity<ApiResponse<FindingResponse>> fillRes = restTemplate.exchange(
                "/api/v1/encounters/" + encounterId + "/findings",
                HttpMethod.POST,
                new HttpEntity<>(fillingM, odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(fillRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<ApiResponse<OdontogramResponse>> odontoResp = restTemplate.exchange(
                "/api/v1/patients/" + patientId + "/odontogram",
                HttpMethod.GET,
                new HttpEntity<>(odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        
        assertThat(odontoResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        OdontogramResponse odonto = odontoResp.getBody().getData();
        assertThat(odonto.findings().stream().filter(f -> "M".equals(f.surfaceCode())).findFirst().orElseThrow().conditionId()).isEqualTo(fillingId);
        assertThat(odonto.cariesSurfaces()).containsEntry((short) 16, 1);
        assertThat(odonto.indices().cpoC()).isEqualTo(1);
    }

    @Test
    void secretariaCannotCreateFinding() {
        CreateFindingRequest caries = new CreateFindingRequest((short) 16, null, "O", 3L, FindingStatus.PENDING, null, null);
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                "/api/v1/encounters/" + encounterId + "/findings",
                HttpMethod.POST,
                new HttpEntity<>(caries, secretariaHeaders),
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
