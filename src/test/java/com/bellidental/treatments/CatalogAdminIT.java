package com.bellidental.treatments;

import com.bellidental.audit.api.dto.AuditEntryResponse;
import com.bellidental.identity.api.dto.AuthResponse;
import com.bellidental.identity.api.dto.LoginRequest;
import com.bellidental.professionals.CreateSpecialtyRequest;
import com.bellidental.professionals.SpecialtyResponse;
import com.bellidental.shared.response.ApiResponse;
import com.bellidental.support.TestcontainersConfiguration;
import com.bellidental.treatments.api.dto.CreateTreatmentRequest;
import com.bellidental.treatments.api.dto.TreatmentResponse;
import com.bellidental.treatments.api.dto.UpdateTreatmentRequest;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CatalogAdminIT {

    private static final String DIRECTOR = "director@bellidental.local";
    private static final String SECRETARIA = "secretaria@bellidental.local";
    private static final String ODONTOLOGA = "odontologa@bellidental.local";
    private static final String SEED_PASSWORD = "Cambiar.2026!";
    private static final Long GENERAL_SPECIALTY_ID = 1L;
    private static final Long ENDODONCIA_SPECIALTY_ID = 2L;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void treatmentLifecycle_asAdmin_createsUpdatesFiltersAndDeactivates() {
        HttpHeaders director = loginAs(DIRECTOR);
        String code = randomCode();
        String name = "Tratamiento de prueba " + code;

        ResponseEntity<ApiResponse<TreatmentResponse>> created = exchange(HttpMethod.POST, "/api/v1/treatments",
                new CreateTreatmentRequest(code, name, null, new BigDecimal("50.00"), ENDODONCIA_SPECIALTY_ID),
                director, new ParameterizedTypeReference<>() {});
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TreatmentResponse treatment = created.getBody().getData();
        assertThat(treatment.active()).isTrue();

        assertThat(codesOf(catalog("?specialtyId=" + GENERAL_SPECIALTY_ID, director))).doesNotContain(code);
        assertThat(codesOf(catalog("?specialtyId=" + ENDODONCIA_SPECIALTY_ID, director))).contains(code);

        ResponseEntity<ApiResponse<TreatmentResponse>> updated = exchange(HttpMethod.PUT,
                "/api/v1/treatments/" + treatment.id(),
                new UpdateTreatmentRequest(name, null, new BigDecimal("55.00"), ENDODONCIA_SPECIALTY_ID),
                director, new ParameterizedTypeReference<>() {});
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().getData().basePrice()).isEqualByComparingTo("55.00");

        ResponseEntity<ApiResponse<TreatmentResponse>> deactivated = exchange(HttpMethod.POST,
                "/api/v1/treatments/" + treatment.id() + "/deactivate", null, director, new ParameterizedTypeReference<>() {});
        assertThat(deactivated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(codesOf(catalog("", director))).doesNotContain(code);
        assertThat(manageList(director))
                .filteredOn(t -> t.code().equals(code))
                .singleElement()
                .satisfies(t -> assertThat(t.active()).isFalse());

        ResponseEntity<ApiResponse<TreatmentResponse>> duplicateCode = exchange(HttpMethod.POST, "/api/v1/treatments",
                new CreateTreatmentRequest(code, "Otro nombre", null, new BigDecimal("10.00"), null),
                director, new ParameterizedTypeReference<>() {});
        assertThat(duplicateCode.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        CreateTreatmentRequest validBody = new CreateTreatmentRequest(randomCode(), "Sin permiso", null, new BigDecimal("10.00"), null);
        assertThat(exchange(HttpMethod.POST, "/api/v1/treatments", validBody, loginAs(SECRETARIA),
                new ParameterizedTypeReference<ApiResponse<TreatmentResponse>>() {}).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange(HttpMethod.POST, "/api/v1/treatments", validBody, loginAs(ODONTOLOGA),
                new ParameterizedTypeReference<ApiResponse<TreatmentResponse>>() {}).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void specialtyLifecycle_asAdmin_rejectsDuplicatesAndInUseDeactivation() {
        HttpHeaders director = loginAs(DIRECTOR);
        String name = "Especialidad de prueba " + randomCode();

        ResponseEntity<ApiResponse<SpecialtyResponse>> created = exchange(HttpMethod.POST, "/api/v1/specialties",
                new CreateSpecialtyRequest(name, 45), director, new ParameterizedTypeReference<>() {});
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long specialtyId = created.getBody().getData().id();

        ResponseEntity<ApiResponse<SpecialtyResponse>> duplicateName = exchange(HttpMethod.POST, "/api/v1/specialties",
                new CreateSpecialtyRequest(name, 30), director, new ParameterizedTypeReference<>() {});
        assertThat(duplicateName.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<ApiResponse<SpecialtyResponse>> invalidDuration = exchange(HttpMethod.POST, "/api/v1/specialties",
                new CreateSpecialtyRequest(name + " X", 20), director, new ParameterizedTypeReference<>() {});
        assertThat(invalidDuration.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<ApiResponse<SpecialtyResponse>> deactivated = exchange(HttpMethod.POST,
                "/api/v1/specialties/" + specialtyId + "/deactivate", null, director, new ParameterizedTypeReference<>() {});
        assertThat(deactivated.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiResponse<List<SpecialtyResponse>>> activeList = exchange(HttpMethod.GET, "/api/v1/specialties",
                null, director, new ParameterizedTypeReference<>() {});
        assertThat(activeList.getBody().getData()).noneMatch(s -> s.id().equals(specialtyId));

        ResponseEntity<ApiResponse<SpecialtyResponse>> inUse = exchange(HttpMethod.POST,
                "/api/v1/specialties/" + GENERAL_SPECIALTY_ID + "/deactivate", null, director,
                new ParameterizedTypeReference<>() {});
        assertThat(inUse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void auditTrail_afterPriceChange_listsOldAndNewPrice() {
        HttpHeaders director = loginAs(DIRECTOR);
        String code = randomCode();
        String name = "Tratamiento auditado " + code;

        ResponseEntity<ApiResponse<TreatmentResponse>> created = exchange(HttpMethod.POST, "/api/v1/treatments",
                new CreateTreatmentRequest(code, name, null, new BigDecimal("30.00"), null),
                director, new ParameterizedTypeReference<>() {});
        Long treatmentId = created.getBody().getData().id();

        ResponseEntity<ApiResponse<TreatmentResponse>> updated = exchange(HttpMethod.PUT,
                "/api/v1/treatments/" + treatmentId,
                new UpdateTreatmentRequest(name, null, new BigDecimal("35.00"), null),
                director, new ParameterizedTypeReference<>() {});
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiResponse<List<AuditEntryResponse>>> history = exchange(HttpMethod.GET,
                "/api/v1/audit/treatments/" + treatmentId, null, director, new ParameterizedTypeReference<>() {});
        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<AuditEntryResponse> entries = history.getBody().getData();
        assertThat(entries).hasSizeGreaterThanOrEqualTo(2);
        AuditEntryResponse lastEntry = entries.get(entries.size() - 1);
        assertThat(lastEntry.type()).isEqualTo("MOD");
        assertThat(lastEntry.changes()).anyMatch(change -> change.field().equals("basePrice"));

        ResponseEntity<ApiResponse<List<AuditEntryResponse>>> bySecretaria = exchange(HttpMethod.GET,
                "/api/v1/audit/treatments/" + treatmentId, null, loginAs(SECRETARIA), new ParameterizedTypeReference<>() {});
        assertThat(bySecretaria.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private ResponseEntity<ApiResponse<List<TreatmentResponse>>> catalog(String queryString, HttpHeaders headers) {
        return exchange(HttpMethod.GET, "/api/v1/treatments" + queryString, null, headers,
                new ParameterizedTypeReference<>() {});
    }

    private List<TreatmentResponse> manageList(HttpHeaders headers) {
        return exchange(HttpMethod.GET, "/api/v1/treatments/manage", null, headers,
                new ParameterizedTypeReference<ApiResponse<List<TreatmentResponse>>>() {}).getBody().getData();
    }

    private List<String> codesOf(ResponseEntity<ApiResponse<List<TreatmentResponse>>> response) {
        return response.getBody().getData().stream().map(TreatmentResponse::code).toList();
    }

    private String randomCode() {
        return "TST" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private HttpHeaders loginAs(String email) {
        ResponseEntity<ApiResponse<AuthResponse>> login = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(new LoginRequest(email, SEED_PASSWORD)),
                new ParameterizedTypeReference<>() {}
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.getBody().getData().accessToken());
        return headers;
    }

    private <T> ResponseEntity<ApiResponse<T>> exchange(HttpMethod method, String url, Object body, HttpHeaders headers,
                                                        ParameterizedTypeReference<ApiResponse<T>> type) {
        return restTemplate.exchange(url, method, new HttpEntity<>(body, headers), type);
    }
}
