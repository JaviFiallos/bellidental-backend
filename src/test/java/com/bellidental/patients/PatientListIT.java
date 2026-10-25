package com.bellidental.patients;

import com.bellidental.appointments.api.dto.AppointmentResponse;
import com.bellidental.appointments.api.dto.CreateAppointmentRequest;
import com.bellidental.billing.api.dto.AccountResponse;
import com.bellidental.billing.api.dto.CreatePaymentRequest;
import com.bellidental.billing.api.dto.PaymentResponse;
import com.bellidental.billing.api.dto.VoidPaymentRequest;
import com.bellidental.clinical.api.dto.EncounterResponse;
import com.bellidental.clinical.api.dto.OpenEncounterRequest;
import com.bellidental.identity.api.dto.AuthResponse;
import com.bellidental.identity.api.dto.LoginRequest;
import com.bellidental.persistence.entity.Professional;
import com.bellidental.persistence.entity.ProfessionalSpecialty;
import com.bellidental.persistence.entity.Treatment;
import com.bellidental.persistence.repository.ProfessionalRepository;
import com.bellidental.persistence.repository.ProfessionalSpecialtyRepository;
import com.bellidental.persistence.repository.TreatmentRepository;
import com.bellidental.persistence.repository.UserRepository;
import com.bellidental.shared.enums.DocumentType;
import com.bellidental.shared.enums.PaymentMethod;
import com.bellidental.shared.response.ApiResponse;
import com.bellidental.support.MutableClock;
import com.bellidental.support.TestcontainersConfiguration;
import com.bellidental.treatments.api.dto.RecordTreatmentRequest;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class PatientListIT {

    private static final AtomicInteger DAY_COUNTER = new AtomicInteger(500);

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private ProfessionalSpecialtyRepository specialtyRepository;

    @Autowired
    private TreatmentRepository treatmentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    private final ZoneId clinicZone = ZoneId.of("America/Guayaquil");

    private HttpHeaders secretariaHeaders;
    private HttpHeaders odontologaHeaders;
    private HttpHeaders patientReaderOnlyHeaders;
    private UUID professionalId;
    private Long specialtyId;

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PageResponse<T>(List<T> content, long totalElements) {}

    @BeforeEach
    void setUp() {
        clock.reset();

        secretariaHeaders = loginAs("secretaria@bellidental.local");
        odontologaHeaders = loginAs("odontologa@bellidental.local");

        setupPatientReaderUser();
        patientReaderOnlyHeaders = loginAs("lector_pacientes@bellidental.local");

        var user = userRepository.findByUsernameAndDeletedAtIsNull("odontologa").orElseThrow();
        Professional prof = professionalRepository.findByUserId(user.getId()).orElseThrow();
        professionalId = prof.getId();

        ProfessionalSpecialty ps = specialtyRepository.findByProfessionalId(professionalId).stream()
                .filter(ProfessionalSpecialty::isActive)
                .findFirst().orElseThrow();
        specialtyId = ps.getSpecialty().getId();
    }

    private void setupPatientReaderUser() {
        jdbcTemplate.update("""
            INSERT INTO roles (role_id, code, name, description)
            VALUES (999, 'SOLO_LECTOR_PACIENTES', 'Lector', 'Test rol sin billing')
            ON CONFLICT DO NOTHING
        """);
        jdbcTemplate.update("""
            INSERT INTO role_permissions (role_id, permission_id)
            VALUES (999, 2)
            ON CONFLICT DO NOTHING
        """);
        jdbcTemplate.update("""
            INSERT INTO users (user_id, username, email, password_hash, is_system, active)
            VALUES ('99999999-9999-9999-9999-999999999999', 'lector_pacientes', 'lector_pacientes@bellidental.local',
                    '$2b$12$7GJX/I3cXGvQAK6yUTKLcOVazmu3MwnnmRmZsa2sN8pftmCDkmflS', false, true)
            ON CONFLICT DO NOTHING
        """);
        jdbcTemplate.update("""
            INSERT INTO user_roles (user_id, role_id)
            VALUES ('99999999-9999-9999-9999-999999999999', 999)
            ON CONFLICT DO NOTHING
        """);
    }

    @Test
    void list_viewAttended_returnsOnlyAttendedMostRecentFirst() {
        String testTag = "ATT" + UUID.randomUUID().toString().substring(0, 5);

        UUID p1 = createPatient(testTag + "A", "Uno");
        UUID p2 = createPatient(testTag + "B", "Dos");
        UUID p3 = createPatient(testTag + "C", "Tres");

        LocalDate day1 = LocalDate.now(clinicZone).minusDays(10);
        LocalDate day2 = LocalDate.now(clinicZone).minusDays(3);

        attendPatientOnDay(p1, day1);
        attendPatientOnDay(p2, day2);
        // p3 has no encounters

        ResponseEntity<ApiResponse<PageResponse<PatientListItem>>> response = exchange(
                HttpMethod.GET,
                "/api/v1/patients/list?view=ATTENDED&query=" + testTag,
                null,
                secretariaHeaders,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PageResponse<PatientListItem> page = response.getBody().getData();
        assertThat(page.totalElements()).isEqualTo(2);

        List<PatientListItem> items = page.content();
        assertThat(items).hasSize(2);

        PatientListItem first = items.get(0);
        PatientListItem second = items.get(1);

        assertThat(first.id()).isEqualTo(p2);
        assertThat(second.id()).isEqualTo(p1);
        assertThat(first.lastEncounterAt()).isNotNull();
        assertThat(second.lastEncounterAt()).isNotNull();
        assertThat(first.lastEncounterAt()).isAfter(second.lastEncounterAt());
    }

    @Test
    void list_viewCreatedWithDateRange_returnsOnlyRegisteredInRangeNewestFirst() {
        String testTag = "CR" + UUID.randomUUID().toString().substring(0, 5);

        UUID p1 = createPatient(testTag + "A", "Antiguo");
        UUID p2 = createPatient(testTag + "B", "Nuevo");

        LocalDate today = LocalDate.now(clinicZone);

        // Query today's range
        ResponseEntity<ApiResponse<PageResponse<PatientListItem>>> inRangeResponse = exchange(
                HttpMethod.GET,
                "/api/v1/patients/list?view=CREATED&from=" + today + "&to=" + today + "&query=" + testTag,
                null,
                secretariaHeaders,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(inRangeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        PageResponse<PatientListItem> page = inRangeResponse.getBody().getData();
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content()).hasSize(2);

        // Newest created first
        assertThat(page.content().get(0).id()).isEqualTo(p2);
        assertThat(page.content().get(1).id()).isEqualTo(p1);

        // Query outside range (yesterday to yesterday)
        LocalDate yesterday = today.minusDays(1);
        ResponseEntity<ApiResponse<PageResponse<PatientListItem>>> outRangeResponse = exchange(
                HttpMethod.GET,
                "/api/v1/patients/list?view=CREATED&from=" + yesterday + "&to=" + yesterday + "&query=" + testTag,
                null,
                secretariaHeaders,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(outRangeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(outRangeResponse.getBody().getData().totalElements()).isEqualTo(0);
    }

    @Test
    void list_withBalance_returnsPendingBalanceMatchingAccountAndFiltersZeroBalance() {
        String testTag = "BAL" + UUID.randomUUID().toString().substring(0, 5);

        UUID patientWithBalance = createPatient(testTag + "1", "ConSaldo");
        UUID patientWithoutBalance = createPatient(testTag + "2", "SinSaldo");

        // Set up treatment of 50.00, payment of 20.00, and voided payment of 10.00 for patientWithBalance
        UUID appointmentId = createCheckedInAppointment(patientWithBalance, LocalDate.now(clinicZone).plusDays(DAY_COUNTER.getAndIncrement()));
        UUID encounterId = openEncounter(appointmentId);

        Treatment treatment = treatmentRepository.findAll().stream()
                .filter(Treatment::isActive)
                .findFirst().orElseThrow();

        ResponseEntity<ApiResponse<Object>> treatmentResp = exchange(
                HttpMethod.POST,
                "/api/v1/encounters/" + encounterId + "/treatments",
                new RecordTreatmentRequest(treatment.getId(), 1, new BigDecimal("50.00"), null, null, null, null, null),
                odontologaHeaders,
                new ParameterizedTypeReference<>() {}
        );
        assertThat(treatmentResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        exchange(
                HttpMethod.POST,
                "/api/v1/encounters/" + encounterId + "/complete",
                null,
                odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<EncounterResponse>>() {}
        );

        // Active payment of 20.00
        exchange(
                HttpMethod.POST,
                "/api/v1/patients/" + patientWithBalance + "/payments",
                new CreatePaymentRequest(new BigDecimal("20.00"), PaymentMethod.EFECTIVO, encounterId),
                secretariaHeaders,
                new ParameterizedTypeReference<ApiResponse<PaymentResponse>>() {}
        );

        // Payment of 10.00, then voided
        ResponseEntity<ApiResponse<PaymentResponse>> voidPaymentResp = exchange(
                HttpMethod.POST,
                "/api/v1/patients/" + patientWithBalance + "/payments",
                new CreatePaymentRequest(new BigDecimal("10.00"), PaymentMethod.EFECTIVO, encounterId),
                secretariaHeaders,
                new ParameterizedTypeReference<>() {}
        );
        UUID paymentToVoidId = voidPaymentResp.getBody().getData().id();

        exchange(
                HttpMethod.POST,
                "/api/v1/payments/" + paymentToVoidId + "/void",
                new VoidPaymentRequest("Anulación por prueba"),
                secretariaHeaders,
                new ParameterizedTypeReference<ApiResponse<PaymentResponse>>() {}
        );

        // Check GET /patients/{id}/account
        ResponseEntity<ApiResponse<AccountResponse>> accountResp = exchange(
                HttpMethod.GET,
                "/api/v1/patients/" + patientWithBalance + "/account",
                null,
                secretariaHeaders,
                new ParameterizedTypeReference<>() {}
        );
        assertThat(accountResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        BigDecimal expectedBalance = accountResp.getBody().getData().balance();
        assertThat(expectedBalance).isEqualByComparingTo("30.00");

        // Query list without withBalance filter: both patients returned
        ResponseEntity<ApiResponse<PageResponse<PatientListItem>>> listResp = exchange(
                HttpMethod.GET,
                "/api/v1/patients/list?view=CREATED&query=" + testTag,
                null,
                secretariaHeaders,
                new ParameterizedTypeReference<>() {}
        );
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResp.getBody().getData().totalElements()).isEqualTo(2);

        PatientListItem itemWithBalance = listResp.getBody().getData().content().stream()
                .filter(i -> i.id().equals(patientWithBalance))
                .findFirst().orElseThrow();
        assertThat(itemWithBalance.pendingBalance()).isEqualByComparingTo(expectedBalance);

        // Query list with withBalance=true: only patient with pending balance > 0 returned
        ResponseEntity<ApiResponse<PageResponse<PatientListItem>>> withBalanceResp = exchange(
                HttpMethod.GET,
                "/api/v1/patients/list?view=CREATED&withBalance=true&query=" + testTag,
                null,
                secretariaHeaders,
                new ParameterizedTypeReference<>() {}
        );
        assertThat(withBalanceResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        PageResponse<PatientListItem> filteredPage = withBalanceResp.getBody().getData();
        assertThat(filteredPage.totalElements()).isEqualTo(1);
        assertThat(filteredPage.content().get(0).id()).isEqualTo(patientWithBalance);
        assertThat(filteredPage.content().get(0).pendingBalance()).isEqualByComparingTo(new BigDecimal("30.00"));
    }

    @Test
    void list_withoutBillingRead_returnsNullBalanceAndForbidsWithBalance() {
        String testTag = "NOBILL" + UUID.randomUUID().toString().substring(0, 5);
        createPatient(testTag, "Test");

        // User without BILLING_READ calling list: pendingBalance should be null
        ResponseEntity<ApiResponse<PageResponse<PatientListItem>>> okResp = exchange(
                HttpMethod.GET,
                "/api/v1/patients/list?view=CREATED&query=" + testTag,
                null,
                patientReaderOnlyHeaders,
                new ParameterizedTypeReference<>() {}
        );
        assertThat(okResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<PatientListItem> content = okResp.getBody().getData().content();
        assertThat(content).isNotEmpty();
        assertThat(content.get(0).pendingBalance()).isNull();

        // User without BILLING_READ calling with withBalance=true: must return 403 Forbidden
        ResponseEntity<ApiResponse<Void>> forbiddenResp = exchange(
                HttpMethod.GET,
                "/api/v1/patients/list?view=CREATED&withBalance=true&query=" + testTag,
                null,
                patientReaderOnlyHeaders,
                new ParameterizedTypeReference<>() {}
        );
        assertThat(forbiddenResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbiddenResp.getBody().getMessage()).contains("No tienes permiso para ver saldos.");
    }

    @Test
    void list_rangeExceedsLimit_returnsBadRequest() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2026, 2, 5); // 400 days

        ResponseEntity<ApiResponse<Void>> response = exchange(
                HttpMethod.GET,
                "/api/v1/patients/list?view=CREATED&from=" + from + "&to=" + to,
                null,
                secretariaHeaders,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("El período no puede superar 366 días.");
    }

    private UUID createPatient(String firstName, String lastName) {
        String randomDigits = String.valueOf(System.nanoTime()).substring(6);
        CreatePatientRequest req = new CreatePatientRequest(
                DocumentType.PASAPORTE, "P" + randomDigits, firstName, lastName,
                LocalDate.of(1992, 4, 15), "F", null, "099" + randomDigits.substring(0, 7),
                null, null, null, null
        );
        ResponseEntity<ApiResponse<PatientResponse>> resp = exchange(
                HttpMethod.POST,
                "/api/v1/patients",
                req,
                secretariaHeaders,
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody().getData().id();
    }

    private void attendPatientOnDay(UUID patientId, LocalDate day) {
        clock.fix(day.atTime(9, 0).atZone(clinicZone).toInstant());
        try {
            UUID appointmentId = createCheckedInAppointment(patientId, day);
            clock.fix(day.atTime(10, 0).atZone(clinicZone).toInstant());
            openEncounter(appointmentId);
        } finally {
            clock.reset();
        }
    }

    private UUID createCheckedInAppointment(UUID patientId, LocalDate day) {
        OffsetDateTime startAt = day.atTime(10, 0).atZone(clinicZone).toOffsetDateTime();
        CreateAppointmentRequest req = new CreateAppointmentRequest(
                patientId, professionalId, specialtyId, startAt, null, "Consulta general", null
        );
        ResponseEntity<ApiResponse<AppointmentResponse>> resp = exchange(
                HttpMethod.POST,
                "/api/v1/appointments",
                req,
                odontologaHeaders,
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID appointmentId = resp.getBody().getData().id();

        exchange(
                HttpMethod.POST,
                "/api/v1/appointments/" + appointmentId + "/check-in",
                null,
                odontologaHeaders,
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
        );
        return appointmentId;
    }

    private UUID openEncounter(UUID appointmentId) {
        ResponseEntity<ApiResponse<EncounterResponse>> resp = exchange(
                HttpMethod.POST,
                "/api/v1/appointments/" + appointmentId + "/encounter",
                new OpenEncounterRequest("Atención general"),
                odontologaHeaders,
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody().getData().id();
    }

    private HttpHeaders loginAs(String email) {
        LoginRequest login = new LoginRequest(email, "Cambiar.2026!");
        ResponseEntity<ApiResponse<AuthResponse>> resp = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(login),
                new ParameterizedTypeReference<>() {}
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(resp.getBody().getData().accessToken());
        return headers;
    }

    private <T> ResponseEntity<ApiResponse<T>> exchange(
            HttpMethod method,
            String path,
            Object body,
            HttpHeaders headers,
            ParameterizedTypeReference<ApiResponse<T>> typeRef
    ) {
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), typeRef);
    }
}
