package com.bellidental.billing;

import com.bellidental.appointments.api.dto.AppointmentResponse;
import com.bellidental.appointments.api.dto.CreateAppointmentRequest;
import com.bellidental.billing.api.dto.AccountResponse;
import com.bellidental.billing.api.dto.AccountSessionResponse;
import com.bellidental.billing.api.dto.CreatePaymentRequest;
import com.bellidental.billing.api.dto.PaymentListItemResponse;
import com.bellidental.billing.api.dto.PaymentResponse;
import com.bellidental.billing.api.dto.VoidPaymentRequest;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class PaymentFlowIT {

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

    private HttpHeaders secretariaHeaders;
    private HttpHeaders odontologaHeaders;
    private UUID patientId;
    private UUID professionalId;
    private Long specialtyId;

    @BeforeEach
    void setUp() {
        clock.reset();
        secretariaHeaders = loginAs("secretaria@bellidental.local");
        odontologaHeaders = loginAs("odontologa@bellidental.local");

        String randomSuffix = UUID.randomUUID().toString().substring(0, 5);
        CreatePatientRequest patientRequest = new CreatePatientRequest(
                DocumentType.PASAPORTE, "B88200" + randomSuffix, "Test", "Payment",
                LocalDate.of(1990, 1, 1), "F", null, "0999999997", null, null, null, null
        );
        ResponseEntity<ApiResponse<PatientSummary>> patientResponse = exchange(
                HttpMethod.POST, "/api/v1/patients", patientRequest, secretariaHeaders,
                new ParameterizedTypeReference<>() {});
        patientId = patientResponse.getBody().getData().id();

        var user = userRepository.findByUsernameAndDeletedAtIsNull("odontologa").orElseThrow();
        professionalId = professionalRepository.findByUserId(user.getId()).orElseThrow().getId();
        specialtyId = specialtyRepository.findByProfessionalId(professionalId).stream()
                .filter(s -> s.isActive())
                .findFirst()
                .orElseThrow()
                .getSpecialty().getId();
    }

    @Test
    void registerPaymentsAndReadAccount_returnsBalanceAndReceipt() {
        UUID encounterId = completedEncounterWithCharge(LocalDate.now().plusDays(101));

        PaymentResponse payment = registerPayment(encounterId, new BigDecimal("15.00"));
        assertThat(payment.receiptNumber()).matches("C-\\d{6}");

        AccountResponse account = getAccount();
        assertThat(account.balance()).isEqualByComparingTo("25.00");
        assertThat(account.sessions()).hasSize(1);
        AccountSessionResponse session = account.sessions().get(0);
        assertThat(session.charge()).isEqualByComparingTo("40.00");

        ResponseEntity<byte[]> receiptResponse = restTemplate.exchange(
                "/api/v1/payments/" + payment.id() + "/receipt",
                HttpMethod.GET,
                new HttpEntity<>(secretariaHeaders),
                byte[].class
        );
        assertThat(receiptResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(receiptResponse.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(new String(receiptResponse.getBody(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    void voidPayment_restoresBalanceAndRequiresReason() {
        UUID encounterId = completedEncounterWithCharge(LocalDate.now().plusDays(102));
        PaymentResponse payment = registerPayment(encounterId, new BigDecimal("15.00"));
        String voidPath = "/api/v1/payments/" + payment.id() + "/void";

        ResponseEntity<ApiResponse<PaymentResponse>> withoutReason = exchange(
                HttpMethod.POST, voidPath, new VoidPaymentRequest(" "), secretariaHeaders,
                new ParameterizedTypeReference<>() {});
        assertThat(withoutReason.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<ApiResponse<PaymentResponse>> withReason = exchange(
                HttpMethod.POST, voidPath, new VoidPaymentRequest("Cobro duplicado"), secretariaHeaders,
                new ParameterizedTypeReference<>() {});
        assertThat(withReason.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(withReason.getBody().getData().voided()).isTrue();

        assertThat(getAccount().balance()).isEqualByComparingTo("40.00");

        ResponseEntity<ApiResponse<PaymentResponse>> voidAgain = exchange(
                HttpMethod.POST, voidPath, new VoidPaymentRequest("Otro motivo"), secretariaHeaders,
                new ParameterizedTypeReference<>() {});
        assertThat(voidAgain.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createAndListPayments_asOdontologo_returnsBothWithVoided() {
        UUID encounterId = completedEncounterWithCharge(LocalDate.now().plusDays(104));

        ResponseEntity<ApiResponse<PaymentResponse>> odoPayment = exchange(
                HttpMethod.POST, "/api/v1/patients/" + patientId + "/payments",
                new CreatePaymentRequest(new BigDecimal("10.00"), PaymentMethod.TARJETA, encounterId), odontologaHeaders,
                new ParameterizedTypeReference<>() {});
        assertThat(odoPayment.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        PaymentResponse secPayment = registerPayment(encounterId, new BigDecimal("5.00"));
        ResponseEntity<ApiResponse<PaymentResponse>> voidResponse = exchange(
                HttpMethod.POST, "/api/v1/payments/" + secPayment.id() + "/void",
                new VoidPaymentRequest("Cobro erróneo"), secretariaHeaders,
                new ParameterizedTypeReference<>() {});
        assertThat(voidResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiResponse<List<PaymentListItemResponse>>> listResponse = exchange(
                HttpMethod.GET, "/api/v1/patients/" + patientId + "/payments", null, odontologaHeaders,
                new ParameterizedTypeReference<>() {});
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<PaymentListItemResponse> items = listResponse.getBody().getData();
        assertThat(items).hasSize(2);

        PaymentListItemResponse first = items.get(0);
        assertThat(first.amount()).isEqualByComparingTo("5.00");
        assertThat(first.voided()).isTrue();
        assertThat(first.voidReason()).isEqualTo("Cobro erróneo");

        PaymentListItemResponse second = items.get(1);
        assertThat(second.amount()).isEqualByComparingTo("10.00");
        assertThat(second.receivedByName()).isEqualTo("odontologa");
    }

    @Test
    void listPayments_unknownPatient_returnsNotFound() {
        ResponseEntity<ApiResponse<List<PaymentListItemResponse>>> response = exchange(
                HttpMethod.GET, "/api/v1/patients/" + UUID.randomUUID() + "/payments", null, odontologaHeaders,
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void downloadReceiptPublic_returnsPdfWithoutBearer() {
        UUID encounterId = completedEncounterWithCharge(LocalDate.now().plusDays(105));
        PaymentResponse payment = registerPayment(encounterId, new BigDecimal("15.00"));

        ResponseEntity<ApiResponse<com.bellidental.security.DocumentLinkResponse>> linkResponse = exchange(
                HttpMethod.POST, "/api/v1/payments/" + payment.id() + "/receipt-link", null, secretariaHeaders,
                new ParameterizedTypeReference<>() {});
        assertThat(linkResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String url = linkResponse.getBody().getData().url();
        assertThat(url).startsWith("/api/v1/payments/" + payment.id() + "/comprobante.pdf?t=");

        ResponseEntity<byte[]> publicPdfResponse = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), byte[].class);

        assertThat(publicPdfResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(publicPdfResponse.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(publicPdfResponse.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(publicPdfResponse.getHeaders().getFirst("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(publicPdfResponse.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(new String(publicPdfResponse.getBody(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    /** Atención completada con un tratamiento de 40.00 (cargo) en un día propio del test. */
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
                new RecordTreatmentRequest(treatmentId, 1, new BigDecimal("40.00"), null, null, null, null, null),
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

    private PaymentResponse registerPayment(UUID encounterId, BigDecimal amount) {
        ResponseEntity<ApiResponse<PaymentResponse>> response = exchange(
                HttpMethod.POST, "/api/v1/patients/" + patientId + "/payments",
                new CreatePaymentRequest(amount, PaymentMethod.EFECTIVO, encounterId), secretariaHeaders,
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().getData();
    }

    private AccountResponse getAccount() {
        ResponseEntity<ApiResponse<AccountResponse>> response = exchange(
                HttpMethod.GET, "/api/v1/patients/" + patientId + "/account", null, secretariaHeaders,
                new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().getData();
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
