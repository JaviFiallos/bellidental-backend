package com.bellidental.patients;

import com.bellidental.identity.api.dto.AuthResponse;
import com.bellidental.identity.api.dto.LoginRequest;
import com.bellidental.shared.enums.BloodType;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea 004a: el tipo de sangre se guarda como etiqueta (varchar(3)) y la API sigue devolviendo el nombre del enum.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class PatientBloodTypeIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private HttpHeaders headers;

    @BeforeEach
    void setUp() {
        LoginRequest login = new LoginRequest("secretaria@bellidental.local", "Cambiar.2026!");
        ResponseEntity<ApiResponse<AuthResponse>> loginResp = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(login),
                new ParameterizedTypeReference<>() {}
        );
        headers = new HttpHeaders();
        headers.setBearerAuth(loginResp.getBody().getData().accessToken());
    }

    @Test
    void createPatient_withLongestBloodType_storesLabelAndReturnsEnum() {
        CreatePatientRequest request = new CreatePatientRequest(
                DocumentType.PASAPORTE, "BT" + UUID.randomUUID().toString().substring(0, 6), "Sangre", "Etiqueta",
                LocalDate.of(1990, 1, 20), "F", BloodType.AB_NEG, "0991112233", null, null, null, null
        );

        ResponseEntity<ApiResponse<PatientResponse>> created = restTemplate.exchange(
                "/api/v1/patients",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().getData().bloodType()).isEqualTo(BloodType.AB_NEG);
        assertThat(created.getBody().getData().bloodTypeLabel()).isEqualTo("AB-");

        String storedLabel = jdbcTemplate.queryForObject(
                "select blood_type from patients where patient_id = ?",
                String.class,
                created.getBody().getData().id()
        );
        assertThat(storedLabel).isEqualTo("AB-");
    }

    @Test
    void updatePatient_changesBloodType() {
        CreatePatientRequest createRequest = new CreatePatientRequest(
                DocumentType.PASAPORTE, "BU" + UUID.randomUUID().toString().substring(0, 6), "Sangre", "Cambio",
                LocalDate.of(1988, 7, 4), "M", BloodType.O_POS, "0990001122", null, null, null, null
        );
        ResponseEntity<ApiResponse<PatientResponse>> created = restTemplate.exchange(
                "/api/v1/patients",
                HttpMethod.POST,
                new HttpEntity<>(createRequest, headers),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID patientId = created.getBody().getData().id();

        UpdatePatientRequest updateRequest = new UpdatePatientRequest(
                null, null, null, null, null, null, BloodType.B_NEG, null, null, null, null, null
        );
        ResponseEntity<ApiResponse<PatientResponse>> updated = restTemplate.exchange(
                "/api/v1/patients/" + patientId,
                HttpMethod.PUT,
                new HttpEntity<>(updateRequest, headers),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().getData().bloodType()).isEqualTo(BloodType.B_NEG);

        ResponseEntity<ApiResponse<PatientResponse>> fetched = restTemplate.exchange(
                "/api/v1/patients/" + patientId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().getData().bloodType()).isEqualTo(BloodType.B_NEG);
        assertThat(fetched.getBody().getData().bloodTypeLabel()).isEqualTo("B-");
    }
}
