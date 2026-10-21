package com.bellidental.dashboard;

import com.bellidental.appointments.api.dto.AppointmentResponse;
import com.bellidental.appointments.api.dto.CreateAppointmentRequest;
import com.bellidental.billing.api.dto.AccountResponse;
import com.bellidental.billing.api.dto.CreatePaymentRequest;
import com.bellidental.billing.api.dto.PaymentResponse;
import com.bellidental.clinical.api.dto.AddDiagnosisRequest;
import com.bellidental.clinical.api.dto.EncounterResponse;
import com.bellidental.clinical.api.dto.Icd10Response;
import com.bellidental.clinical.api.dto.OpenEncounterRequest;
import com.bellidental.dashboard.api.dto.DashboardSummaryResponse;
import com.bellidental.dashboard.api.dto.ProfessionalStats;
import com.bellidental.identity.api.dto.AuthResponse;
import com.bellidental.identity.api.dto.LoginRequest;
import com.bellidental.patients.CreatePatientRequest;
import com.bellidental.patients.PatientSummary;
import com.bellidental.persistence.repository.ProfessionalRepository;
import com.bellidental.persistence.repository.ProfessionalSpecialtyRepository;
import com.bellidental.persistence.repository.UserRepository;
import com.bellidental.shared.enums.DiagnosisCertainty;
import com.bellidental.shared.enums.DocumentType;
import com.bellidental.shared.enums.PaymentMethod;
import com.bellidental.shared.response.ApiResponse;
import com.bellidental.support.MutableClock;
import com.bellidental.support.TestcontainersConfiguration;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class DashboardSummaryIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private ProfessionalSpecialtyRepository specialtyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MutableClock clock;

    private HttpHeaders directorHeaders;
    private HttpHeaders secretariaHeaders;
    private HttpHeaders odontologaHeaders;
    private UUID patientId;
    private UUID professionalId;
    private Long specialtyId;

    @BeforeEach
    void setUp() {
        // Otros IT del bot dejan el reloj compartido fijado a otra fecha: el caso necesita la hora real.
        clock.reset();
        directorHeaders = loginAs("director@bellidental.local");
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
    void summary_asAdminWithData_returnsExpectedFigures() {
        LocalDate today = LocalDate.now(ZoneId.of("America/Guayaquil"));
        DashboardSummaryResponse before = getSummary(directorHeaders, today, today);
        createPatient();

        UUID encounterId = completedEncounterWithCharge(LocalDate.now().plusDays(111));
        registerPayment(encounterId, new BigDecimal("30.00"));

        DashboardSummaryResponse after = getSummary(directorHeaders, today, today);

        assertThat(after.patients().newInRange() - before.patients().newInRange()).isEqualTo(1);
        assertThat(after.appointments().completedEncounters() - before.appointments().completedEncounters()).isEqualTo(1);
        assertThat(after.billing().collected().subtract(before.billing().collected())).isEqualByComparingTo("30.00");
        assertThat(after.billing().byMethod().get("EFECTIVO").subtract(before.billing().byMethod().get("EFECTIVO")))
                .isEqualByComparingTo("30.00");
        assertThat(after.billing().outstandingBalance().subtract(before.billing().outstandingBalance()))
                .isEqualByComparingTo("50.00");

        ProfessionalStats professionalBefore = professionalStats(before, professionalId);
        ProfessionalStats professionalAfter = professionalStats(after, professionalId);
        assertThat(professionalAfter.encounters() - professionalBefore.encounters()).isEqualTo(1);
        assertThat(professionalAfter.revenue().subtract(professionalBefore.revenue())).isEqualByComparingTo("80.00");

        assertThat(getAccount().balance()).isEqualByComparingTo("50.00");
    }

    @Test
    void summary_asSecretaria_returnsForbidden() {
        ResponseEntity<ApiResponse<DashboardSummaryResponse>> response = exchange(
                HttpMethod.GET, "/api/v1/dashboard/summary", null, secretariaHeaders,
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /** Paciente nuevo: se crea dentro del test, después de la lectura «antes», para que cuente como alta de hoy. */
    private void createPatient() {
        String randomSuffix = UUID.randomUUID().toString().substring(0, 5);
        CreatePatientRequest patientRequest = new CreatePatientRequest(
                DocumentType.PASAPORTE, "B88300" + randomSuffix, "Test", "Dashboard",
                LocalDate.of(1990, 1, 1), "F", null, "0999999996", null, null, null, null
        );
        ResponseEntity<ApiResponse<PatientSummary>> patientResponse = exchange(
                HttpMethod.POST, "/api/v1/patients", patientRequest, secretariaHeaders,
                new ParameterizedTypeReference<>() {});
        patientId = patientResponse.getBody().getData().id();
    }

    private DashboardSummaryResponse getSummary(HttpHeaders headers, LocalDate from, LocalDate to) {
        ResponseEntity<ApiResponse<DashboardSummaryResponse>> response = exchange(
                HttpMethod.GET, "/api/v1/dashboard/summary?from=" + from + "&to=" + to, null, headers,
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().getData();
    }

    private ProfessionalStats professionalStats(DashboardSummaryResponse summary, UUID id) {
        return summary.byProfessional().stream()
                .filter(stats -> stats.professionalId().equals(id))
                .findFirst()
                .orElse(new ProfessionalStats(id, null, 0, BigDecimal.ZERO));
    }

    private AccountResponse getAccount() {
        ResponseEntity<ApiResponse<AccountResponse>> response = exchange(
                HttpMethod.GET, "/api/v1/patients/" + patientId + "/account", null, secretariaHeaders,
                new ParameterizedTypeReference<>() {});
        return response.getBody().getData();
    }

    private void registerPayment(UUID encounterId, BigDecimal amount) {
        ResponseEntity<ApiResponse<PaymentResponse>> response = exchange(
                HttpMethod.POST, "/api/v1/patients/" + patientId + "/payments",
                new CreatePaymentRequest(amount, PaymentMethod.EFECTIVO, encounterId), secretariaHeaders,
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    /** Atención completada con un tratamiento de 40.00 × 2 (cargo 80.00) en un día propio del test. */
    private UUID completedEncounterWithCharge(LocalDate day) {
        UUID appointmentId = createCheckedInAppointment(day);

        ResponseEntity<ApiResponse<EncounterResponse>> openResponse = exchange(
                HttpMethod.POST, "/api/v1/appointments/" + appointmentId + "/encounter",
                new OpenEncounterRequest("Restauración"), odontologaHeaders, new ParameterizedTypeReference<>() {});
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID encounterId = openResponse.getBody().getData().id();

        Long icd10Id = findIcd10Id("K02.1");
        ResponseEntity<ApiResponse<Object>> diagnosisResponse = exchange(
                HttpMethod.POST, "/api/v1/encounters/" + encounterId + "/diagnoses",
                new AddDiagnosisRequest(icd10Id, DiagnosisCertainty.DEFINITIVE, null), odontologaHeaders,
                new ParameterizedTypeReference<>() {});
        assertThat(diagnosisResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Long treatmentId = searchTreatments("resina").get(0).id();
        ResponseEntity<ApiResponse<Object>> treatmentResponse = exchange(
                HttpMethod.POST, "/api/v1/encounters/" + encounterId + "/treatments",
                new RecordTreatmentRequest(treatmentId, 2, new BigDecimal("40.00"), null, null, null, null, null),
                odontologaHeaders, new ParameterizedTypeReference<>() {});
        assertThat(treatmentResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<ApiResponse<EncounterResponse>> completeResponse = exchange(
                HttpMethod.POST, "/api/v1/encounters/" + encounterId + "/complete", null, odontologaHeaders,
                new ParameterizedTypeReference<>() {});
        assertThat(completeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        return encounterId;
    }

    /** Cada test crea su propia cita con check-in; dos citas del mismo profesional en la misma franja chocan. */
    private UUID createCheckedInAppointment(LocalDate day) {
        OffsetDateTime startAt = day.atTime(10, 0)
                .atZone(ZoneId.of("America/Guayaquil"))
                .toOffsetDateTime();
        CreateAppointmentRequest appointmentRequest = new CreateAppointmentRequest(
                patientId, professionalId, specialtyId, startAt, null, "Restauración", null
        );
        ResponseEntity<ApiResponse<AppointmentResponse>> appointmentResponse = exchange(
                HttpMethod.POST, "/api/v1/appointments", appointmentRequest, odontologaHeaders,
                new ParameterizedTypeReference<>() {});
        assertThat(appointmentResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID appointmentId = appointmentResponse.getBody().getData().id();

        exchange(HttpMethod.POST, "/api/v1/appointments/" + appointmentId + "/check-in", null, odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<Void>>() {});
        return appointmentId;
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

    private List<TreatmentResponse> searchTreatments(String query) {
        ResponseEntity<ApiResponse<List<TreatmentResponse>>> response = exchange(
                HttpMethod.GET, "/api/v1/treatments?query=" + query, null, odontologaHeaders,
                new ParameterizedTypeReference<>() {});
        return response.getBody().getData();
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
