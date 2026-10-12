package com.bellidental.clinicalrecords;

import com.bellidental.identity.api.dto.AuthResponse;
import com.bellidental.identity.api.dto.LoginRequest;
import com.bellidental.patients.CreatePatientRequest;
import com.bellidental.patients.PatientSummary;
import com.bellidental.shared.enums.DocumentType;
import com.bellidental.shared.enums.HistoryScope;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ClinicalRecordApiIT {

    @Autowired
    private TestRestTemplate restTemplate;

    private HttpHeaders secretariaHeaders;
    private HttpHeaders odontologaHeaders;

    @BeforeEach
    void setUp() {
        secretariaHeaders = loginAs("secretaria@bellidental.local");
        odontologaHeaders = loginAs("odontologa@bellidental.local");
    }

    private UUID createPatient() {
        String randomSuffix = UUID.randomUUID().toString().substring(0, 5);
        CreatePatientRequest patientRequest = new CreatePatientRequest(
                DocumentType.PASAPORTE, "B88300" + randomSuffix, "Test", "RecordApi",
                LocalDate.of(1990, 1, 1), "F", null, "0999999998", null, null, null, null
        );
        ResponseEntity<ApiResponse<PatientSummary>> patientResponse = restTemplate.exchange(
                "/api/v1/patients",
                HttpMethod.POST,
                new HttpEntity<>(patientRequest, secretariaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(patientResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return patientResponse.getBody().getData().id();
    }

    @Test
    void clinicalRecord_wrappedRoundTrip() {
        UUID patientId = createPatient();

        // 1. POST …/clinical-record → 201 con success, data.recordNumber y data.historyEntries == []
        ResponseEntity<ApiResponse<ClinicalRecordResponse>> openResponse = restTemplate.exchange(
                "/api/v1/patients/" + patientId + "/clinical-record",
                HttpMethod.POST,
                new HttpEntity<>(odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(openResponse.getBody().isSuccess()).isTrue();
        assertThat(openResponse.getBody().getMessage()).isEqualTo("Historia clínica abierta.");
        assertThat(openResponse.getBody().getData().recordNumber()).isNotBlank();
        assertThat(openResponse.getBody().getData().historyEntries()).isEmpty();

        UUID recordId = openResponse.getBody().getData().id();

        // 2. GET /medical-history-conditions → data con 10 condiciones y data[9].name == "Otro"
        ResponseEntity<ApiResponse<List<MedicalHistoryConditionResponse>>> conditionsResponse = restTemplate.exchange(
                "/api/v1/medical-history-conditions",
                HttpMethod.GET,
                new HttpEntity<>(odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(conditionsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(conditionsResponse.getBody().getData()).hasSize(10);
        assertThat(conditionsResponse.getBody().getData().get(9).name()).isEqualTo("Otro");

        Long diabetesId = conditionsResponse.getBody().getData().stream()
                .filter(c -> "Diabetes".equals(c.name()))
                .findFirst()
                .map(MedicalHistoryConditionResponse::id)
                .orElseThrow();

        // 3. POST …/history-entries (Diabetes, PERSONAL) → 201 con data.conditionName == "Diabetes"
        CreateMedicalHistoryEntryRequest entryReq = new CreateMedicalHistoryEntryRequest(
                diabetesId, HistoryScope.PERSONAL, null, null
        );
        ResponseEntity<ApiResponse<MedicalHistoryEntryResponse>> entryResponse = restTemplate.exchange(
                "/api/v1/clinical-records/" + recordId + "/history-entries",
                HttpMethod.POST,
                new HttpEntity<>(entryReq, odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(entryResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(entryResponse.getBody().getData().conditionName()).isEqualTo("Diabetes");

        UUID entryId = entryResponse.getBody().getData().id();

        // 4. GET …/clinical-record → data.historyEntries[0].active == true
        ResponseEntity<ApiResponse<ClinicalRecordResponse>> getResponse = restTemplate.exchange(
                "/api/v1/patients/" + patientId + "/clinical-record",
                HttpMethod.GET,
                new HttpEntity<>(odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().getData().historyEntries()).hasSize(1);
        assertThat(getResponse.getBody().getData().historyEntries().get(0).active()).isTrue();

        // 5. POST /history-entries/{id}/void → 200 con el message "Antecedente anulado."
        ResponseEntity<ApiResponse<Void>> voidResponse = restTemplate.exchange(
                "/api/v1/history-entries/" + entryId + "/void",
                HttpMethod.POST,
                new HttpEntity<>(odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(voidResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(voidResponse.getBody().getMessage()).isEqualTo("Antecedente anulado.");
    }

    @Test
    void updateClinicalRecord_medicationClearsAndNoKnownHistoryConflict() {
        UUID patientId = createPatient();

        ResponseEntity<ApiResponse<ClinicalRecordResponse>> openResponse = restTemplate.exchange(
                "/api/v1/patients/" + patientId + "/clinical-record",
                HttpMethod.POST,
                new HttpEntity<>(odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(openResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID recordId = openResponse.getBody().getData().id();

        // PUT con currentMedication: "  metformina  " → data.currentMedication == "metformina"
        UpdateClinicalRecordRequest req1 = new UpdateClinicalRecordRequest(null, "  metformina  ");
        ResponseEntity<ApiResponse<ClinicalRecordResponse>> put1 = restTemplate.exchange(
                "/api/v1/clinical-records/" + recordId,
                HttpMethod.PUT,
                new HttpEntity<>(req1, odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(put1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(put1.getBody().getData().currentMedication()).isEqualTo("metformina");

        // PUT con currentMedication: "" → data.currentMedication == null
        UpdateClinicalRecordRequest req2 = new UpdateClinicalRecordRequest(null, "");
        ResponseEntity<ApiResponse<ClinicalRecordResponse>> put2 = restTemplate.exchange(
                "/api/v1/clinical-records/" + recordId,
                HttpMethod.PUT,
                new HttpEntity<>(req2, odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(put2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(put2.getBody().getData().currentMedication()).isNull();

        // Registrar un antecedente activo
        CreateMedicalHistoryEntryRequest entryReq = new CreateMedicalHistoryEntryRequest(
                1L, HistoryScope.PERSONAL, null, null
        );
        ResponseEntity<ApiResponse<MedicalHistoryEntryResponse>> entryResponse = restTemplate.exchange(
                "/api/v1/clinical-records/" + recordId + "/history-entries",
                HttpMethod.POST,
                new HttpEntity<>(entryReq, odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(entryResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // PUT con noKnownHistory: true → 409 con el message del servicio
        UpdateClinicalRecordRequest req3 = new UpdateClinicalRecordRequest(true, null);
        ResponseEntity<ApiResponse<ClinicalRecordResponse>> put3 = restTemplate.exchange(
                "/api/v1/clinical-records/" + recordId,
                HttpMethod.PUT,
                new HttpEntity<>(req3, odontologaHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(put3.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(put3.getBody().getMessage()).contains("No se puede marcar como sin antecedentes mientras haya antecedentes activos.");
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
