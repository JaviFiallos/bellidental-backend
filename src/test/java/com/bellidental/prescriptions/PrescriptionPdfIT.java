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
import com.bellidental.prescriptions.api.dto.MedicationResponse;
import com.bellidental.prescriptions.api.dto.PrescriptionItemRequest;
import com.bellidental.prescriptions.api.dto.PrescriptionRequest;
import com.bellidental.prescriptions.api.dto.PrescriptionResponse;
import com.bellidental.shared.enums.DiagnosisCertainty;
import com.bellidental.shared.enums.DocumentType;
import com.bellidental.shared.response.ApiResponse;
import com.bellidental.support.TestcontainersConfiguration;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class PrescriptionPdfIT {

    private static final LocalDate APPOINTMENT_DAY = LocalDate.now().plusDays(92);
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
    private PatientSummary patient;
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

        patient = createPatient();
        encounterId = createEncounter(patient.id());
    }

    @Test
    void downloadPdf_issuedPrescription_returnsPdfWithConfidentialAndPatientDocument() throws IOException {
        Long medicationId = searchMedications("amo").getFirst().id();
        UUID diagnosisId = addDiagnosis(encounterId);
        PrescriptionItemRequest item = new PrescriptionItemRequest(medicationId, null, "1 cápsula", "cada 8 horas", "7 días", "21 cápsulas", "Tomar con alimentos");
        PrescriptionResponse created = createPrescription(encounterId, new PrescriptionRequest(diagnosisId, "Reposo relativo", List.of(item)));

        exchange(HttpMethod.POST, "/api/v1/prescriptions/" + created.id() + "/issue", null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<PrescriptionResponse>>() {});

        ResponseEntity<byte[]> response = restTemplate.exchange(
                "/api/v1/prescriptions/" + created.id() + "/pdf", HttpMethod.GET,
                new HttpEntity<>(odontologaHeaders), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        byte[] pdf = response.getBody();
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        String text = extractText(pdf);
        assertThat(text).contains("CONFIDENCIAL");
        assertThat(text).contains(patient.documentNumber());
        assertThat(text).contains("Presente esta receta en su próxima consulta");
        assertThat(text).contains("Sello");
        assertThat(text).contains("Firma");
        assertThat(text).contains("Los mejores tratamientos");
        assertThat(text).contains("+593 96 219 8909");
        assertThat(text).doesNotContain("Twitter");

        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target/prescription-sample.pdf"), pdf);
    }

    @Test
    void downloadPdf_draftPrescription_returnsConflict() {
        Long medicationId = searchMedications("amo").getFirst().id();
        UUID diagnosisId = addDiagnosis(encounterId);
        PrescriptionItemRequest item = new PrescriptionItemRequest(medicationId, null, "1 cápsula", "cada 8 horas", "7 días", null, null);
        PrescriptionResponse created = createPrescription(encounterId, new PrescriptionRequest(diagnosisId, null, List.of(item)));

        ResponseEntity<ApiResponse<Void>> response = exchange(
                HttpMethod.GET, "/api/v1/prescriptions/" + created.id() + "/pdf", null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<Void>>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("Solo se puede imprimir una receta emitida.");
    }

    @Test
    void downloadPdfPublic_issuedPrescription_returnsPdfWithoutBearer() {
        Long medicationId = searchMedications("amo").getFirst().id();
        UUID diagnosisId = addDiagnosis(encounterId);
        PrescriptionItemRequest item = new PrescriptionItemRequest(medicationId, null, "1 cápsula", "cada 8 horas", "7 días", "21 cápsulas", null);
        PrescriptionResponse created = createPrescription(encounterId, new PrescriptionRequest(diagnosisId, "Tomar con agua", List.of(item)));

        exchange(HttpMethod.POST, "/api/v1/prescriptions/" + created.id() + "/issue", null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<PrescriptionResponse>>() {});

        ResponseEntity<ApiResponse<com.bellidental.security.DocumentLinkResponse>> linkResponse = exchange(
                HttpMethod.POST, "/api/v1/prescriptions/" + created.id() + "/pdf-link", null, odontologaHeaders,
                new ParameterizedTypeReference<>() {});
        assertThat(linkResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String url = linkResponse.getBody().getData().url();
        assertThat(url).startsWith("/api/v1/prescriptions/" + created.id() + "/receta.pdf?t=");

        ResponseEntity<byte[]> publicPdfResponse = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), byte[].class);

        assertThat(publicPdfResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(publicPdfResponse.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(publicPdfResponse.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(publicPdfResponse.getHeaders().getFirst("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(publicPdfResponse.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(new String(publicPdfResponse.getBody(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    void downloadPdfPublic_tokenFromAnotherPrescription_returnsForbidden() {
        Long medicationId = searchMedications("amo").getFirst().id();
        UUID diagnosisId = addDiagnosis(encounterId);
        PrescriptionItemRequest item = new PrescriptionItemRequest(medicationId, null, "1 cápsula", "cada 8 horas", "7 días", "21 cápsulas", null);
        PrescriptionResponse rx1 = createPrescription(encounterId, new PrescriptionRequest(diagnosisId, "Receta 1", List.of(item)));
        PrescriptionResponse rx2 = createPrescription(encounterId, new PrescriptionRequest(diagnosisId, "Receta 2", List.of(item)));

        exchange(HttpMethod.POST, "/api/v1/prescriptions/" + rx1.id() + "/issue", null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<PrescriptionResponse>>() {});
        exchange(HttpMethod.POST, "/api/v1/prescriptions/" + rx2.id() + "/issue", null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<PrescriptionResponse>>() {});

        ResponseEntity<ApiResponse<com.bellidental.security.DocumentLinkResponse>> linkResponse = exchange(
                HttpMethod.POST, "/api/v1/prescriptions/" + rx1.id() + "/pdf-link", null, odontologaHeaders,
                new ParameterizedTypeReference<>() {});
        String token1 = linkResponse.getBody().getData().url().split("\\?t=")[1];

        ResponseEntity<ApiResponse<Void>> forbiddenResponse = restTemplate.exchange(
                "/api/v1/prescriptions/" + rx2.id() + "/receta.pdf?t=" + token1,
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(forbiddenResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbiddenResponse.getBody().getMessage()).isEqualTo("El enlace del documento venció o no es válido.");
    }

    @Test
    void useDocumentTokenAsBearer_onProtectedEndpoint_returnsUnauthorized() {
        Long medicationId = searchMedications("amo").getFirst().id();
        UUID diagnosisId = addDiagnosis(encounterId);
        PrescriptionItemRequest item = new PrescriptionItemRequest(medicationId, null, "1 cápsula", "cada 8 horas", "7 días", "21 cápsulas", null);
        PrescriptionResponse created = createPrescription(encounterId, new PrescriptionRequest(diagnosisId, "Receta token", List.of(item)));

        exchange(HttpMethod.POST, "/api/v1/prescriptions/" + created.id() + "/issue", null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<PrescriptionResponse>>() {});

        ResponseEntity<ApiResponse<com.bellidental.security.DocumentLinkResponse>> linkResponse = exchange(
                HttpMethod.POST, "/api/v1/prescriptions/" + created.id() + "/pdf-link", null, odontologaHeaders,
                new ParameterizedTypeReference<>() {});
        String token = linkResponse.getBody().getData().url().split("\\?t=")[1];

        HttpHeaders docTokenHeaders = new HttpHeaders();
        docTokenHeaders.setBearerAuth(token);

        ResponseEntity<ApiResponse<Void>> meResponse = restTemplate.exchange(
                "/api/v1/auth/me",
                HttpMethod.GET,
                new HttpEntity<>(docTokenHeaders),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String extractText(byte[] pdf) throws IOException {
        PdfReader reader = new PdfReader(pdf);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(extractor.getTextFromPage(page));
            }
            return text.toString();
        } finally {
            reader.close();
        }
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

    private PatientSummary createPatient() {
        String randomSuffix = UUID.randomUUID().toString().substring(0, 5);
        CreatePatientRequest request = new CreatePatientRequest(
                DocumentType.PASAPORTE, "B99400" + randomSuffix, "Receta", "Paciente",
                LocalDate.of(1990, 1, 1), "F", null, "0999999999", null, null, null, null
        );
        ResponseEntity<ApiResponse<PatientSummary>> response = exchange(
                HttpMethod.POST, "/api/v1/patients", request, secretariaHeaders,
                new ParameterizedTypeReference<ApiResponse<PatientSummary>>() {});
        return response.getBody().getData();
    }

    private UUID createEncounter(UUID patientId) {
        OffsetDateTime startAt = APPOINTMENT_DAY.atTime(nextStartHour++, 0)
                .atZone(ZoneId.of("America/Guayaquil"))
                .toOffsetDateTime();
        CreateAppointmentRequest appointmentRequest = new CreateAppointmentRequest(
                patientId, professionalId, specialtyId, startAt, null, "Receta PDF", null
        );
        ResponseEntity<ApiResponse<AppointmentResponse>> appointmentResponse = exchange(
                HttpMethod.POST, "/api/v1/appointments", appointmentRequest, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<AppointmentResponse>>() {});
        assertThat(appointmentResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID appointmentId = appointmentResponse.getBody().getData().id();

        exchange(HttpMethod.POST, "/api/v1/appointments/" + appointmentId + "/check-in", null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<Void>>() {});

        ResponseEntity<ApiResponse<EncounterResponse>> openResponse = exchange(
                HttpMethod.POST, "/api/v1/appointments/" + appointmentId + "/encounter", new OpenEncounterRequest("Receta PDF"), odontologaHeaders,
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
