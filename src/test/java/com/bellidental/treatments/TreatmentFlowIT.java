package com.bellidental.treatments;

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
import com.bellidental.shared.enums.EncounterStatus;
import com.bellidental.shared.enums.FindingStatus;
import com.bellidental.shared.response.ApiResponse;
import com.bellidental.support.TestcontainersConfiguration;
import com.bellidental.treatments.api.dto.PerformedTreatmentResponse;
import com.bellidental.treatments.api.dto.RecordTreatmentRequest;
import com.bellidental.treatments.api.dto.TreatmentResponse;
import com.bellidental.treatments.api.dto.VoidTreatmentRequest;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class TreatmentFlowIT {

    private static final LocalDate APPOINTMENT_DAY = LocalDate.now().plusDays(70);
    private static int nextStartHour = 8;

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
    private UUID professionalId;
    private Long specialtyId;
    private UUID patientId;
    private UUID encounterId;

    @BeforeEach
    void setUp() {
        secretariaHeaders = loginAs("secretaria@bellidental.local");
        odontologaHeaders = loginAs("odontologa@bellidental.local");

        var user = userRepository.findByUsernameAndDeletedAtIsNull("odontologa").orElseThrow();
        professionalId = professionalRepository.findByUserId(user.getId()).orElseThrow().getId();
        specialtyId = specialtyRepository.findByProfessionalId(professionalId).stream()
                .filter(s -> s.isActive())
                .findFirst()
                .orElseThrow()
                .getSpecialty().getId();

        patientId = createPatient();
        encounterId = createEncounter(patientId);
    }

    @Test
    void recordTreatment_withoutPrice_copiesBasePriceAndLinksDiagnosis() {
        List<TreatmentResponse> resinas = searchTreatments("resina");
        assertThat(resinas).isNotEmpty();
        TreatmentResponse resina = resinas.getFirst();
        UUID diagnosisId = addDiagnosis(encounterId);

        RecordTreatmentRequest request = new RecordTreatmentRequest(resina.id(), 2, null, diagnosisId, null, null, null, null);
        ResponseEntity<ApiResponse<PerformedTreatmentResponse>> response = exchange(
                HttpMethod.POST, "/api/v1/encounters/" + encounterId + "/treatments", request, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<PerformedTreatmentResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PerformedTreatmentResponse recorded = response.getBody().getData();
        assertThat(recorded.price()).isEqualByComparingTo(resina.basePrice());
        assertThat(recorded.total()).isEqualByComparingTo(resina.basePrice().multiply(BigDecimal.valueOf(2)));
        assertThat(recorded.encounterDiagnosisId()).isEqualTo(diagnosisId);

        assertThat(listEncounterTreatments(encounterId)).extracting(PerformedTreatmentResponse::id).contains(recorded.id());
    }

    @Test
    void voidTreatment_withoutReasonThenWithReason_excludesFromList() {
        PerformedTreatmentResponse recorded = recordTreatment(encounterId, new RecordTreatmentRequest(anyTreatmentId(), null, null, null, null, null, null, null));

        ResponseEntity<ApiResponse<Void>> withoutReason = exchange(
                HttpMethod.POST, "/api/v1/performed-treatments/" + recorded.id() + "/void", new VoidTreatmentRequest(""), odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<Void>>() {});
        assertThat(withoutReason.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<ApiResponse<Void>> withReason = exchange(
                HttpMethod.POST, "/api/v1/performed-treatments/" + recorded.id() + "/void", new VoidTreatmentRequest("Error de digitación"), odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<Void>>() {});
        assertThat(withReason.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(listEncounterTreatments(encounterId)).extracting(PerformedTreatmentResponse::id).doesNotContain(recorded.id());
    }

    @Test
    void recordTreatment_diagnosisFromOtherPatient_returnsConflict() {
        UUID otherPatientId = createPatient();
        UUID otherEncounterId = createEncounter(otherPatientId);
        UUID otherDiagnosisId = addDiagnosis(otherEncounterId);

        RecordTreatmentRequest request = new RecordTreatmentRequest(anyTreatmentId(), null, null, otherDiagnosisId, null, null, null, null);
        ResponseEntity<ApiResponse<PerformedTreatmentResponse>> response = exchange(
                HttpMethod.POST, "/api/v1/encounters/" + encounterId + "/treatments", request, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<PerformedTreatmentResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("El diagnóstico no pertenece a este paciente.");
    }

    @Test
    void recordTreatment_onPendingFindingFromPreviousEncounter_createsDoneFinding() {
        UUID dentalFindingId = createFinding(encounterId, conditionIdByCode("CARIES"), (short) 16, "M");
        UUID currentEncounterId = createEncounter(patientId);

        RecordTreatmentRequest request = new RecordTreatmentRequest(anyTreatmentId(), null, null, null, null, null, null, dentalFindingId);
        ResponseEntity<ApiResponse<PerformedTreatmentResponse>> response = exchange(
                HttpMethod.POST, "/api/v1/encounters/" + currentEncounterId + "/treatments", request, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<PerformedTreatmentResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PerformedTreatmentResponse recorded = response.getBody().getData();
        assertThat(recorded.dentalFindingId()).isEqualTo(dentalFindingId);
        assertThat(recorded.toothCode()).isEqualTo((short) 16);
        assertThat(recorded.surfaceCode()).isEqualTo("M");

        List<FindingResponse> surfaceFindings = getPatientOdontogram(patientId).findings().stream()
                .filter(f -> f.toothCode() == 16 && "M".equals(f.surfaceCode()))
                .toList();
        assertThat(surfaceFindings).hasSize(1);
        assertThat(surfaceFindings.getFirst().status()).isEqualTo(FindingStatus.DONE);
        assertThat(surfaceFindings.getFirst().conditionId()).isEqualTo(conditionIdByCode("FILLING"));
    }

    @Test
    void recordTreatment_findingFromOtherPatient_returnsConflict() {
        UUID otherPatientId = createPatient();
        UUID otherEncounterId = createEncounter(otherPatientId);
        UUID otherFindingId = createFinding(otherEncounterId, conditionIdByCode("CARIES"), (short) 16, "O");

        RecordTreatmentRequest request = new RecordTreatmentRequest(anyTreatmentId(), null, null, null, null, null, null, otherFindingId);
        ResponseEntity<ApiResponse<PerformedTreatmentResponse>> response = exchange(
                HttpMethod.POST, "/api/v1/encounters/" + encounterId + "/treatments", request, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<PerformedTreatmentResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("El hallazgo no pertenece a este paciente.");
    }

    @Test
    void recordTreatment_orthodonticsControlWithZeroPriceAndZ464_completesEncounter() {
        List<TreatmentResponse> treatments = searchTreatments("D86");
        assertThat(treatments).extracting(TreatmentResponse::code).contains("D8670");
        TreatmentResponse d8670 = treatments.stream()
                .filter(t -> "D8670".equals(t.code()))
                .findFirst()
                .orElseThrow();
        assertThat(d8670.basePrice()).isEqualByComparingTo(BigDecimal.ZERO);

        ResponseEntity<ApiResponse<List<Icd10Response>>> icd10Response = exchange(
                HttpMethod.GET, "/api/v1/icd10?query=Z46", null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<List<Icd10Response>>>() {});
        assertThat(icd10Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Long z464Id = icd10Response.getBody().getData().stream()
                .filter(c -> "Z46.4".equals(c.code()))
                .findFirst()
                .orElseThrow()
                .id();

        AddDiagnosisRequest diagnosisRequest = new AddDiagnosisRequest(z464Id, DiagnosisCertainty.DEFINITIVE, "Control de ortodoncia");
        ResponseEntity<ApiResponse<DiagnosisResponse>> diagnosisResponse = exchange(
                HttpMethod.POST, "/api/v1/encounters/" + encounterId + "/diagnoses", diagnosisRequest, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<DiagnosisResponse>>() {});
        assertThat(diagnosisResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID diagnosisId = diagnosisResponse.getBody().getData().id();

        RecordTreatmentRequest treatmentRequest = new RecordTreatmentRequest(
                d8670.id(), 1, BigDecimal.ZERO, diagnosisId, null, null, null, null
        );
        ResponseEntity<ApiResponse<PerformedTreatmentResponse>> recordResponse = exchange(
                HttpMethod.POST, "/api/v1/encounters/" + encounterId + "/treatments", treatmentRequest, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<PerformedTreatmentResponse>>() {});
        assertThat(recordResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PerformedTreatmentResponse performed = recordResponse.getBody().getData();
        assertThat(performed.price()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(performed.total()).isEqualByComparingTo(BigDecimal.ZERO);

        ResponseEntity<ApiResponse<EncounterResponse>> completeResponse = exchange(
                HttpMethod.POST, "/api/v1/encounters/" + encounterId + "/complete", null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<EncounterResponse>>() {});
        assertThat(completeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(completeResponse.getBody().getData().status()).isEqualTo(EncounterStatus.COMPLETED);
    }

    private UUID createFinding(UUID encounterId, Long conditionId, Short toothCode, String surfaceCode) {
        CreateFindingRequest request = new CreateFindingRequest(toothCode, null, surfaceCode, conditionId, FindingStatus.PENDING, null, null);
        ResponseEntity<ApiResponse<FindingResponse>> response = exchange(
                HttpMethod.POST, "/api/v1/encounters/" + encounterId + "/findings", request, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<FindingResponse>>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().getData().id();
    }

    private Long conditionIdByCode(String conditionCode) {
        ResponseEntity<ApiResponse<CatalogResponse>> response = exchange(
                HttpMethod.GET, "/api/v1/odontogram/catalog", null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<CatalogResponse>>() {});
        return response.getBody().getData().conditions().stream()
                .filter(c -> conditionCode.equals(c.code()))
                .findFirst()
                .orElseThrow()
                .id();
    }

    private OdontogramResponse getPatientOdontogram(UUID patientId) {
        ResponseEntity<ApiResponse<OdontogramResponse>> response = exchange(
                HttpMethod.GET, "/api/v1/patients/" + patientId + "/odontogram", null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<OdontogramResponse>>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().getData();
    }

    private PerformedTreatmentResponse recordTreatment(UUID encounterId, RecordTreatmentRequest request) {
        ResponseEntity<ApiResponse<PerformedTreatmentResponse>> response = exchange(
                HttpMethod.POST, "/api/v1/encounters/" + encounterId + "/treatments", request, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<PerformedTreatmentResponse>>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().getData();
    }

    private List<TreatmentResponse> searchTreatments(String query) {
        ResponseEntity<ApiResponse<List<TreatmentResponse>>> response = exchange(
                HttpMethod.GET, "/api/v1/treatments?query=" + query, null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<List<TreatmentResponse>>>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().getData();
    }

    private Long anyTreatmentId() {
        return searchTreatments("resina").getFirst().id();
    }

    private List<PerformedTreatmentResponse> listEncounterTreatments(UUID encounterId) {
        ResponseEntity<ApiResponse<List<PerformedTreatmentResponse>>> response = exchange(
                HttpMethod.GET, "/api/v1/encounters/" + encounterId + "/treatments", null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<List<PerformedTreatmentResponse>>>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().getData();
    }

    private UUID addDiagnosis(UUID encounterId) {
        ResponseEntity<ApiResponse<List<Icd10Response>>> icd10Response = exchange(
                HttpMethod.GET, "/api/v1/icd10?query=K02", null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<List<Icd10Response>>>() {});
        Long icd10Id = icd10Response.getBody().getData().getFirst().id();

        AddDiagnosisRequest request = new AddDiagnosisRequest(icd10Id, DiagnosisCertainty.DEFINITIVE, null);
        ResponseEntity<ApiResponse<DiagnosisResponse>> response = exchange(
                HttpMethod.POST, "/api/v1/encounters/" + encounterId + "/diagnoses", request, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<DiagnosisResponse>>() {});
        return response.getBody().getData().id();
    }

    private UUID createPatient() {
        String randomSuffix = UUID.randomUUID().toString().substring(0, 5);
        CreatePatientRequest request = new CreatePatientRequest(
                DocumentType.PASAPORTE, "B99200" + randomSuffix, "Tratamiento", "Paciente",
                LocalDate.of(1990, 1, 1), "F", null, "0999999999", null, null, null, null
        );
        ResponseEntity<ApiResponse<PatientSummary>> response = exchange(
                HttpMethod.POST, "/api/v1/patients", request, secretariaHeaders,
                new ParameterizedTypeReference<ApiResponse<PatientSummary>>() {});
        return response.getBody().getData().id();
    }

    private UUID createEncounter(UUID patientId) {
        OffsetDateTime startAt = APPOINTMENT_DAY.atTime(nextStartHour++, 0)
                .atZone(ZoneId.of("America/Guayaquil"))
                .toOffsetDateTime();
        CreateAppointmentRequest appointmentRequest = new CreateAppointmentRequest(
                patientId, professionalId, specialtyId, startAt, null, "Tratamiento", null
        );
        ResponseEntity<ApiResponse<AppointmentResponse>> appointmentResponse = exchange(
                HttpMethod.POST, "/api/v1/appointments", appointmentRequest, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<AppointmentResponse>>() {});
        assertThat(appointmentResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID appointmentId = appointmentResponse.getBody().getData().id();

        exchange(HttpMethod.POST, "/api/v1/appointments/" + appointmentId + "/check-in", null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<Void>>() {});

        ResponseEntity<ApiResponse<EncounterResponse>> openResponse = exchange(
                HttpMethod.POST, "/api/v1/appointments/" + appointmentId + "/encounter", new OpenEncounterRequest("Tratamiento"), odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<EncounterResponse>>() {});
        return openResponse.getBody().getData().id();
    }

    private <T> ResponseEntity<ApiResponse<T>> exchange(HttpMethod method, String url, Object body, HttpHeaders headers,
                                                        ParameterizedTypeReference<ApiResponse<T>> responseType) {
        return restTemplate.exchange(url, method, new HttpEntity<>(body, headers), responseType);
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
