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
import com.bellidental.prescriptions.api.dto.MedicationPresetRequest;
import com.bellidental.prescriptions.api.dto.MedicationPresetResponse;
import com.bellidental.prescriptions.api.dto.MedicationRequest;
import com.bellidental.prescriptions.api.dto.MedicationResponse;
import com.bellidental.prescriptions.api.dto.PrescriptionItemRequest;
import com.bellidental.prescriptions.api.dto.PrescriptionRequest;
import com.bellidental.prescriptions.api.dto.PrescriptionResponse;
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
class MedicationCatalogIT {

    private static final LocalDate APPOINTMENT_DAY = LocalDate.now().plusDays(210);
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
    }

    @Test
    void createMedicationWithPreset_thenUpdatePreset_searchReflectsNewDose() {
        String name = uniqueName();
        MedicationResponse medication = createMedication(name, "Tableta", "500 mg");
        MedicationPresetResponse preset = createPreset(medication.id(), "Cada 8 h", "1 tableta");

        MedicationResponse found = searchMedications(name).getFirst();
        assertThat(found.presets()).extracting(MedicationPresetResponse::id).containsExactly(preset.id());

        ResponseEntity<ApiResponse<MedicationPresetResponse>> updated = exchange(
                HttpMethod.PUT, "/api/v1/medication-presets/" + preset.id(),
                new MedicationPresetRequest("Cada 8 h", "2 tabletas", "cada 8 horas", "3 días", null), odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<MedicationPresetResponse>>() {});

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(searchMedications(name).getFirst().presets().getFirst().dose()).isEqualTo("2 tabletas");
    }

    @Test
    void deactivatePreset_hidesItFromSearchAndShowsItInManage() {
        String name = uniqueName();
        MedicationResponse medication = createMedication(name, "Tableta", "250 mg");
        MedicationPresetResponse preset = createPreset(medication.id(), "Cada 12 h", "1 tableta");

        ResponseEntity<ApiResponse<MedicationPresetResponse>> deactivated = exchange(
                HttpMethod.POST, "/api/v1/medication-presets/" + preset.id() + "/deactivate", null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<MedicationPresetResponse>>() {});
        assertThat(deactivated.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(searchMedications(name).getFirst().presets()).isEmpty();

        MedicationResponse inManage = listManage().stream()
                .filter(m -> m.id().equals(medication.id()))
                .findFirst()
                .orElseThrow();
        assertThat(inManage.presets().getFirst().active()).isFalse();
    }

    @Test
    void updateMedication_usedInPrescription_returnsConflict() {
        String name = uniqueName();
        MedicationResponse medication = createMedication(name, "Tableta", "10 mg");
        UUID patientId = createPatient();
        UUID encounterId = createEncounter(patientId);
        UUID diagnosisId = addDiagnosis(encounterId);
        PrescriptionItemRequest item = new PrescriptionItemRequest(medication.id(), null, "1 tableta", "cada 8 horas", "3 días", null, null);
        createPrescription(encounterId, new PrescriptionRequest(diagnosisId, null, List.of(item)));

        ResponseEntity<ApiResponse<MedicationResponse>> response = exchange(
                HttpMethod.PUT, "/api/v1/medications/" + medication.id(),
                new MedicationRequest(name, "Tableta", "20 mg", null), odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<MedicationResponse>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("El medicamento ya se usó en recetas: desactívalo y crea uno nuevo.");
    }

    private String uniqueName() {
        return "ItCatalogo" + UUID.randomUUID().toString().substring(0, 8).replace("-", "");
    }

    private MedicationResponse createMedication(String name, String presentation, String concentration) {
        ResponseEntity<ApiResponse<MedicationResponse>> response = exchange(
                HttpMethod.POST, "/api/v1/medications", new MedicationRequest(name, presentation, concentration, "Oral"), odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<MedicationResponse>>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().getData();
    }

    private MedicationPresetResponse createPreset(Long medicationId, String label, String dose) {
        ResponseEntity<ApiResponse<MedicationPresetResponse>> response = exchange(
                HttpMethod.POST, "/api/v1/medications/" + medicationId + "/presets",
                new MedicationPresetRequest(label, dose, "cada 8 horas", "3 días", null), odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<MedicationPresetResponse>>() {});
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

    private List<MedicationResponse> listManage() {
        ResponseEntity<ApiResponse<List<MedicationResponse>>> response = exchange(
                HttpMethod.GET, "/api/v1/medications/manage", null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<List<MedicationResponse>>>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().getData();
    }

    private PrescriptionResponse createPrescription(UUID encounterId, PrescriptionRequest request) {
        ResponseEntity<ApiResponse<PrescriptionResponse>> response = exchange(
                HttpMethod.POST, "/api/v1/encounters/" + encounterId + "/prescriptions", request, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<PrescriptionResponse>>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
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
                DocumentType.PASAPORTE, "B99400" + randomSuffix, "Catalogo", "Paciente",
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
                patientId, professionalId, specialtyId, startAt, null, "Catálogo", null
        );
        ResponseEntity<ApiResponse<AppointmentResponse>> appointmentResponse = exchange(
                HttpMethod.POST, "/api/v1/appointments", appointmentRequest, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<AppointmentResponse>>() {});
        assertThat(appointmentResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID appointmentId = appointmentResponse.getBody().getData().id();

        exchange(HttpMethod.POST, "/api/v1/appointments/" + appointmentId + "/check-in", null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<Void>>() {});
        ResponseEntity<ApiResponse<EncounterResponse>> openResponse = exchange(
                HttpMethod.POST, "/api/v1/appointments/" + appointmentId + "/encounter", new OpenEncounterRequest("Catálogo"), odontologaHeaders,
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
