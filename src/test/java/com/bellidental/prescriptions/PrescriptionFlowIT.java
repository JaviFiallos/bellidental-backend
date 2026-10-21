package com.bellidental.prescriptions;

import com.bellidental.appointments.api.dto.AppointmentResponse;
import com.bellidental.appointments.api.dto.CreateAppointmentRequest;
import com.bellidental.clinical.api.dto.AddDiagnosisRequest;
import com.bellidental.clinical.api.dto.DiagnosisResponse;
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
import com.bellidental.prescriptions.api.dto.MedicationPresetResponse;
import com.bellidental.prescriptions.api.dto.MedicationResponse;
import com.bellidental.prescriptions.api.dto.PrescriptionItemRequest;
import com.bellidental.prescriptions.api.dto.PrescriptionItemResponse;
import com.bellidental.prescriptions.api.dto.PrescriptionRequest;
import com.bellidental.prescriptions.api.dto.PrescriptionResponse;
import com.bellidental.shared.enums.DiagnosisCertainty;
import com.bellidental.shared.enums.DocumentType;
import com.bellidental.shared.enums.PrescriptionStatus;
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
class PrescriptionFlowIT {

    private static final LocalDate APPOINTMENT_DAY = LocalDate.now().plusDays(90);
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
    void createAndIssue_withPreset_copiesPosologyAndBecomesIssued() {
        MedicationResponse amoxicilina = searchMedications("amo").stream()
                .filter(m -> "Amoxicilina".equals(m.name()))
                .findFirst()
                .orElseThrow();
        MedicationPresetResponse preset = amoxicilina.presets().getFirst();
        UUID diagnosisId = addDiagnosis(encounterId);

        PrescriptionItemRequest item = new PrescriptionItemRequest(amoxicilina.id(), preset.id(), null, null, null, "21 cápsulas", null);
        PrescriptionResponse created = createPrescription(encounterId, new PrescriptionRequest(diagnosisId, null, List.of(item)));

        assertThat(created.status()).isEqualTo(PrescriptionStatus.DRAFT);
        PrescriptionItemResponse createdItem = created.items().getFirst();
        assertThat(createdItem.dose()).isEqualTo(preset.dose());
        assertThat(createdItem.frequency()).isEqualTo(preset.frequency());
        assertThat(createdItem.duration()).isEqualTo(preset.duration());
        assertThat(createdItem.quantity()).isEqualTo("21 cápsulas");

        ResponseEntity<ApiResponse<PrescriptionResponse>> issueResponse = exchange(
                HttpMethod.POST, "/api/v1/prescriptions/" + created.id() + "/issue", null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<PrescriptionResponse>>() {});

        assertThat(issueResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(issueResponse.getBody().getData().status()).isEqualTo(PrescriptionStatus.ISSUED);
    }

    @Test
    void updatePrescription_whenIssued_returnsConflict() {
        Long medicationId = searchMedications("amo").getFirst().id();
        UUID diagnosisId = addDiagnosis(encounterId);
        PrescriptionItemRequest item = new PrescriptionItemRequest(medicationId, null, "1 cápsula", "cada 8 horas", "7 días", null, null);
        PrescriptionResponse created = createPrescription(encounterId, new PrescriptionRequest(diagnosisId, null, List.of(item)));

        exchange(HttpMethod.POST, "/api/v1/prescriptions/" + created.id() + "/issue", null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<PrescriptionResponse>>() {});

        ResponseEntity<ApiResponse<PrescriptionResponse>> response = exchange(
                HttpMethod.PUT, "/api/v1/prescriptions/" + created.id(),
                new PrescriptionRequest(diagnosisId, "Cambio tardío", List.of(item)), odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<PrescriptionResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("Solo se puede modificar una receta en borrador.");
    }

    @Test
    void createPrescription_diagnosisFromOtherEncounter_returnsConflict() {
        UUID otherEncounterId = createEncounter(patientId);
        UUID otherDiagnosisId = addDiagnosis(otherEncounterId);
        Long medicationId = searchMedications("amo").getFirst().id();
        PrescriptionItemRequest item = new PrescriptionItemRequest(medicationId, null, "1 cápsula", "cada 8 horas", "7 días", null, null);

        ResponseEntity<ApiResponse<PrescriptionResponse>> response = exchange(
                HttpMethod.POST, "/api/v1/encounters/" + encounterId + "/prescriptions",
                new PrescriptionRequest(otherDiagnosisId, null, List.of(item)), odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<PrescriptionResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("El diagnóstico no pertenece a esta atención.");
    }

    private PrescriptionResponse createPrescription(UUID encounterId, PrescriptionRequest request) {
        ResponseEntity<ApiResponse<PrescriptionResponse>> response = exchange(
                HttpMethod.POST, "/api/v1/encounters/" + encounterId + "/prescriptions", request, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<PrescriptionResponse>>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().getData();
    }

    private List<MedicationResponse> searchMedications(String query) {
        ResponseEntity<ApiResponse<List<MedicationResponse>>> response = exchange(
                HttpMethod.GET, "/api/v1/medications?query=" + query, null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<List<MedicationResponse>>>() {});
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
                DocumentType.PASAPORTE, "B99300" + randomSuffix, "Receta", "Paciente",
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
                patientId, professionalId, specialtyId, startAt, null, "Receta", null
        );
        ResponseEntity<ApiResponse<AppointmentResponse>> appointmentResponse = exchange(
                HttpMethod.POST, "/api/v1/appointments", appointmentRequest, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<AppointmentResponse>>() {});
        assertThat(appointmentResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID appointmentId = appointmentResponse.getBody().getData().id();

        exchange(HttpMethod.POST, "/api/v1/appointments/" + appointmentId + "/check-in", null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<Void>>() {});

        ResponseEntity<ApiResponse<EncounterResponse>> openResponse = exchange(
                HttpMethod.POST, "/api/v1/appointments/" + appointmentId + "/encounter", new OpenEncounterRequest("Receta"), odontologaHeaders,
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
