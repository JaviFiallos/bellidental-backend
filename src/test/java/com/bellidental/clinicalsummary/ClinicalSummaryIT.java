package com.bellidental.clinicalsummary;

import com.bellidental.appointments.api.dto.AppointmentResponse;
import com.bellidental.appointments.api.dto.CreateAppointmentRequest;
import com.bellidental.clinical.api.dto.AddDiagnosisRequest;
import com.bellidental.clinical.api.dto.DiagnosisResponse;
import com.bellidental.clinical.api.dto.EncounterResponse;
import com.bellidental.clinical.api.dto.Icd10Response;
import com.bellidental.clinical.api.dto.OpenEncounterRequest;
import com.bellidental.clinical.api.dto.VoidEncounterRequest;
import com.bellidental.clinicalsummary.api.dto.ClinicalSummaryResponse;
import com.bellidental.identity.api.dto.AuthResponse;
import com.bellidental.identity.api.dto.LoginRequest;
import com.bellidental.patients.CreatePatientRequest;
import com.bellidental.patients.PatientSummary;
import com.bellidental.persistence.repository.ProfessionalRepository;
import com.bellidental.persistence.repository.ProfessionalSpecialtyRepository;
import com.bellidental.persistence.repository.UserRepository;
import com.bellidental.prescriptions.api.dto.MedicationResponse;
import com.bellidental.prescriptions.api.dto.PrescriptionItemRequest;
import com.bellidental.prescriptions.api.dto.PrescriptionRequest;
import com.bellidental.prescriptions.api.dto.PrescriptionResponse;
import com.bellidental.shared.enums.DiagnosisCertainty;
import com.bellidental.shared.enums.DocumentType;
import com.bellidental.shared.response.ApiResponse;
import com.bellidental.support.TestcontainersConfiguration;
import com.bellidental.treatments.api.dto.PerformedTreatmentResponse;
import com.bellidental.treatments.api.dto.RecordTreatmentRequest;
import com.bellidental.treatments.api.dto.TreatmentResponse;
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
class ClinicalSummaryIT {

    private static final LocalDate APPOINTMENT_DAY = LocalDate.now().plusDays(150);
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
    void getSummary_twoEncountersAndOneVoided_returnsNumberedSessionsWithDiagnosesTreatmentsAndIssuedPrescriptions() {
        UUID patientId = createPatient();

        // Primera atención: con diagnóstico, tratamiento con pieza y receta emitida + receta en borrador
        UUID encounter1Id = createEncounter(patientId);
        UUID diagnosis1Id = addDiagnosis(encounter1Id, "K02", DiagnosisCertainty.DEFINITIVE, "Caries en esmalte");
        TreatmentResponse treatment = searchTreatments("resina").getFirst();
        recordTreatment(encounter1Id, new RecordTreatmentRequest(
                treatment.id(), 1, null, diagnosis1Id, (short) 16, "O", "Obturación clase II", null
        ));

        Long medId = searchMedications("amo").getFirst().id();
        PrescriptionItemRequest item = new PrescriptionItemRequest(medId, null, "500 mg", "cada 8 horas", "7 días", "21 cápsulas", "Tomar con alimentos");
        PrescriptionResponse prescription1 = createPrescription(encounter1Id, new PrescriptionRequest(diagnosis1Id, "Indicaciones generales", List.of(item)));
        issuePrescription(prescription1.id());

        // Segunda receta en misma atención 1 (permanece como borrador)
        createPrescription(encounter1Id, new PrescriptionRequest(diagnosis1Id, "Receta no emitida", List.of(item)));

        // Segunda atención: se anula
        UUID encounter2Id = createEncounter(patientId);
        voidEncounter(encounter2Id, "Atención cancelada por error de registro");

        // Tercera atención: abierta/vigente
        UUID encounter3Id = createEncounter(patientId);

        ResponseEntity<ApiResponse<ClinicalSummaryResponse>> response = exchange(
                HttpMethod.GET, "/api/v1/patients/" + patientId + "/clinical-summary", null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<ClinicalSummaryResponse>>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ClinicalSummaryResponse summary = response.getBody().getData();
        assertThat(summary.patientId()).isEqualTo(patientId);
        assertThat(summary.clinicName()).isNotBlank();
        assertThat(summary.recordNumber()).isNotNull().startsWith("HC-");
        assertThat(summary.openedAt()).isNotNull();
        assertThat(summary.openedBy()).isNotNull();
        assertThat(summary.openedBy().licenseNumber()).isNotBlank();

        // Debe haber exactamente 2 sesiones numeradas 1 y 2 en orden de fecha
        assertThat(summary.sessions()).hasSize(2);

        ClinicalSummaryResponse.Session session1 = summary.sessions().get(0);
        assertThat(session1.sessionNumber()).isEqualTo(1);
        assertThat(session1.encounterId()).isEqualTo(encounter1Id);
        assertThat(session1.professional()).isNotNull();
        assertThat(session1.professional().licenseNumber()).isNotBlank();

        assertThat(session1.diagnoses()).hasSize(1);
        assertThat(session1.diagnoses().getFirst().certainty()).isEqualTo(DiagnosisCertainty.DEFINITIVE);
        assertThat(session1.diagnoses().getFirst().notes()).isEqualTo("Caries en esmalte");

        assertThat(session1.treatments()).hasSize(1);
        assertThat(session1.treatments().getFirst().code()).isEqualTo(treatment.code());
        assertThat(session1.treatments().getFirst().toothCode()).isEqualTo((short) 16);
        assertThat(session1.treatments().getFirst().surfaceCode()).isEqualTo("O");

        // Solo la receta emitida debe figurar, la en borrador no
        assertThat(session1.prescriptions()).hasSize(1);
        ClinicalSummaryResponse.PrescriptionItem presItem = session1.prescriptions().getFirst();
        assertThat(presItem.id()).isEqualTo(prescription1.id());
        assertThat(presItem.items()).hasSize(1);
        assertThat(presItem.items().getFirst().medicationName()).containsIgnoringCase("Amoxicilina");

        ClinicalSummaryResponse.Session session2 = summary.sessions().get(1);
        assertThat(session2.sessionNumber()).isEqualTo(2);
        assertThat(session2.encounterId()).isEqualTo(encounter3Id);
    }

    @Test
    void getSummary_patientWithoutEncounters_returnsEmptySessions() {
        UUID patientId = createPatient();

        ResponseEntity<ApiResponse<ClinicalSummaryResponse>> response = exchange(
                HttpMethod.GET, "/api/v1/patients/" + patientId + "/clinical-summary", null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<ClinicalSummaryResponse>>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ClinicalSummaryResponse summary = response.getBody().getData();
        assertThat(summary.patientId()).isEqualTo(patientId);
        assertThat(summary.sessions()).isEmpty();
        assertThat(summary.recordNumber()).isNull();
        assertThat(summary.openedAt()).isNull();
        assertThat(summary.openedBy()).isNull();
    }

    @Test
    void getSummary_asSecretary_returnsForbidden() {
        UUID patientId = createPatient();

        ResponseEntity<ApiResponse<ClinicalSummaryResponse>> response = exchange(
                HttpMethod.GET, "/api/v1/patients/" + patientId + "/clinical-summary", null, secretariaHeaders,
                new ParameterizedTypeReference<ApiResponse<ClinicalSummaryResponse>>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private UUID createPatient() {
        String randomSuffix = UUID.randomUUID().toString().substring(0, 5);
        CreatePatientRequest request = new CreatePatientRequest(
                DocumentType.PASAPORTE, "P" + randomSuffix, "Historial", "Paciente",
                LocalDate.of(1992, 5, 10), "M", null, "0991112233", null, null, null, null
        );
        ResponseEntity<ApiResponse<PatientSummary>> response = exchange(
                HttpMethod.POST, "/api/v1/patients", request, secretariaHeaders,
                new ParameterizedTypeReference<ApiResponse<PatientSummary>>() {}
        );
        return response.getBody().getData().id();
    }

    private UUID createEncounter(UUID patientId) {
        OffsetDateTime startAt = APPOINTMENT_DAY.atTime(nextStartHour++, 0)
                .atZone(ZoneId.of("America/Guayaquil"))
                .toOffsetDateTime();
        CreateAppointmentRequest appointmentRequest = new CreateAppointmentRequest(
                patientId, professionalId, specialtyId, startAt, null, "Consulta general", null
        );
        ResponseEntity<ApiResponse<AppointmentResponse>> appointmentResponse = exchange(
                HttpMethod.POST, "/api/v1/appointments", appointmentRequest, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<AppointmentResponse>>() {}
        );
        assertThat(appointmentResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID appointmentId = appointmentResponse.getBody().getData().id();

        exchange(HttpMethod.POST, "/api/v1/appointments/" + appointmentId + "/check-in", null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<Void>>() {});

        ResponseEntity<ApiResponse<EncounterResponse>> openResponse = exchange(
                HttpMethod.POST, "/api/v1/appointments/" + appointmentId + "/encounter", new OpenEncounterRequest("Motivo de consulta"), odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<EncounterResponse>>() {});
        return openResponse.getBody().getData().id();
    }

    private UUID addDiagnosis(UUID encounterId, String codeQuery, DiagnosisCertainty certainty, String notes) {
        ResponseEntity<ApiResponse<List<Icd10Response>>> icd10Response = exchange(
                HttpMethod.GET, "/api/v1/icd10?query=" + codeQuery, null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<List<Icd10Response>>>() {}
        );
        Long icd10Id = icd10Response.getBody().getData().getFirst().id();

        AddDiagnosisRequest request = new AddDiagnosisRequest(icd10Id, certainty, notes);
        ResponseEntity<ApiResponse<DiagnosisResponse>> response = exchange(
                HttpMethod.POST, "/api/v1/encounters/" + encounterId + "/diagnoses", request, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<DiagnosisResponse>>() {}
        );
        return response.getBody().getData().id();
    }

    private List<TreatmentResponse> searchTreatments(String query) {
        ResponseEntity<ApiResponse<List<TreatmentResponse>>> response = exchange(
                HttpMethod.GET, "/api/v1/treatments?query=" + query, null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<List<TreatmentResponse>>>() {}
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().getData();
    }

    private void recordTreatment(UUID encounterId, RecordTreatmentRequest request) {
        ResponseEntity<ApiResponse<PerformedTreatmentResponse>> response = exchange(
                HttpMethod.POST, "/api/v1/encounters/" + encounterId + "/treatments", request, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<PerformedTreatmentResponse>>() {}
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private List<MedicationResponse> searchMedications(String query) {
        ResponseEntity<ApiResponse<List<MedicationResponse>>> response = exchange(
                HttpMethod.GET, "/api/v1/medications?query=" + query, null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<List<MedicationResponse>>>() {}
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().getData();
    }

    private PrescriptionResponse createPrescription(UUID encounterId, PrescriptionRequest request) {
        ResponseEntity<ApiResponse<PrescriptionResponse>> response = exchange(
                HttpMethod.POST, "/api/v1/encounters/" + encounterId + "/prescriptions", request, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<PrescriptionResponse>>() {}
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().getData();
    }

    private void issuePrescription(UUID prescriptionId) {
        ResponseEntity<ApiResponse<PrescriptionResponse>> response = exchange(
                HttpMethod.POST, "/api/v1/prescriptions/" + prescriptionId + "/issue", null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<PrescriptionResponse>>() {}
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void voidEncounter(UUID encounterId, String reason) {
        ResponseEntity<ApiResponse<EncounterResponse>> response = exchange(
                HttpMethod.POST, "/api/v1/encounters/" + encounterId + "/void", new VoidEncounterRequest(reason), odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<EncounterResponse>>() {}
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
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
