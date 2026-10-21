package com.bellidental.bot;

import com.bellidental.bot.api.dto.BotPatientResponse;
import com.bellidental.bot.api.dto.ClinicInfoResponse;
import com.bellidental.bot.api.dto.ConsentRequest;
import com.bellidental.bot.api.dto.ConsentResponse;
import com.bellidental.bot.api.dto.VerifyPatientRequest;
import com.bellidental.identity.api.dto.AuthResponse;
import com.bellidental.identity.api.dto.LoginRequest;
import com.bellidental.patients.CreatePatientRequest;
import com.bellidental.patients.PatientResponse;
import com.bellidental.shared.enums.BloodType;
import com.bellidental.shared.enums.DocumentType;
import com.bellidental.shared.response.ApiResponse;
import com.bellidental.support.TestcontainersConfiguration;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class BotApiIT {

    private static final String BOT_KEY = "it-bot-key";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private HttpHeaders botHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Bot-Key", BOT_KEY);
        return headers;
    }

    private String loginAsSecretaria() {
        LoginRequest login = new LoginRequest("secretaria@bellidental.local", "Cambiar.2026!");
        ResponseEntity<ApiResponse<AuthResponse>> resp = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(login),
                new ParameterizedTypeReference<>() {}
        );
        return resp.getBody().getData().accessToken();
    }

    @Test
    void botApi_withoutOrWrongKeyOrStaffJwt_isRejected() {
        // 1. sin header → 401
        ResponseEntity<ApiResponse<ClinicInfoResponse>> withoutHeader = restTemplate.exchange(
                "/api/v1/bot/info",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                new ParameterizedTypeReference<>() {}
        );
        assertThat(withoutHeader.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 2. clave errónea → 401
        HttpHeaders wrongHeaders = new HttpHeaders();
        wrongHeaders.set("X-Bot-Key", "wrong-key");
        ResponseEntity<ApiResponse<ClinicInfoResponse>> wrongKey = restTemplate.exchange(
                "/api/v1/bot/info",
                HttpMethod.GET,
                new HttpEntity<>(wrongHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(wrongKey.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 3. JWT de secretaria en /api/v1/bot/info → 403
        String staffToken = loginAsSecretaria();
        HttpHeaders staffHeaders = new HttpHeaders();
        staffHeaders.setBearerAuth(staffToken);
        ResponseEntity<ApiResponse<ClinicInfoResponse>> staffJwt = restTemplate.exchange(
                "/api/v1/bot/info",
                HttpMethod.GET,
                new HttpEntity<>(staffHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(staffJwt.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 4. X-Bot-Key válida en GET /api/v1/patients → 401
        ResponseEntity<String> botOnStaffEndpoint = restTemplate.exchange(
                "/api/v1/patients",
                HttpMethod.GET,
                new HttpEntity<>(botHeaders()),
                String.class
        );
        assertThat(botOnStaffEndpoint.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void consent_acceptThenQuery_isAcceptedAndIdempotent() {
        String testPhone = "59398" + UUID.randomUUID().toString().replace("-", "").substring(0, 7);

        // 1. GET /consents → accepted=false
        ResponseEntity<ApiResponse<ConsentResponse>> initialGet = restTemplate.exchange(
                "/api/v1/bot/consents?phone=" + testPhone,
                HttpMethod.GET,
                new HttpEntity<>(botHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(initialGet.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(initialGet.getBody()).isNotNull();
        assertThat(initialGet.getBody().getData().accepted()).isFalse();
        assertThat(initialGet.getBody().getData().noticeVersion()).isEqualTo("v1");

        // 2. POST dos veces → 201
        ConsentRequest request = new ConsentRequest(testPhone, null);
        ResponseEntity<ApiResponse<ConsentResponse>> post1 = restTemplate.exchange(
                "/api/v1/bot/consents",
                HttpMethod.POST,
                new HttpEntity<>(request, botHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(post1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(post1.getBody().getData().accepted()).isTrue();

        ResponseEntity<ApiResponse<ConsentResponse>> post2 = restTemplate.exchange(
                "/api/v1/bot/consents",
                HttpMethod.POST,
                new HttpEntity<>(request, botHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(post2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(post2.getBody().getData().accepted()).isTrue();

        // 3. GET → accepted=true
        ResponseEntity<ApiResponse<ConsentResponse>> finalGet = restTemplate.exchange(
                "/api/v1/bot/consents?phone=" + testPhone,
                HttpMethod.GET,
                new HttpEntity<>(botHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(finalGet.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(finalGet.getBody().getData().accepted()).isTrue();

        // 4. en la BD hay una sola fila para ese teléfono
        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM privacy_consents WHERE phone_e164 = ?",
                Integer.class,
                testPhone
        );
        assertThat(rowCount).isEqualTo(1);
    }

    private static int idCounter = 1;

    private synchronized String nextValidId() {
        int seq = (int) (System.currentTimeMillis() % 800000) + (++idCounter);
        String first9 = String.format("17%07d", seq);
        int[] coefs = {2, 1, 2, 1, 2, 1, 2, 1, 2};
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            int p = (first9.charAt(i) - '0') * coefs[i];
            if (p >= 10) p -= 9;
            sum += p;
        }
        int check = (sum % 10 == 0) ? 0 : 10 - (sum % 10);
        return first9 + check;
    }

    @Test
    void identify_byPhoneAndVerify_returnsDisplayNameOnly() {
        String staffToken = loginAsSecretaria();
        HttpHeaders staffHeaders = new HttpHeaders();
        staffHeaders.setBearerAuth(staffToken);

        String sharedPhone = "098" + UUID.randomUUID().toString().replace("-", "").substring(0, 7);
        String docNumber1 = nextValidId();
        String docNumber2 = nextValidId();

        // Crear dos pacientes con el mismo teléfono por la API de personal
        CreatePatientRequest p1 = new CreatePatientRequest(
                DocumentType.CI, docNumber1, "Carlos Alberto", "Perez Gomez",
                LocalDate.of(1985, 5, 10), "M", BloodType.O_POS, sharedPhone, null, null, null, null
        );
        ResponseEntity<ApiResponse<PatientResponse>> created1 = restTemplate.exchange(
                "/api/v1/patients",
                HttpMethod.POST,
                new HttpEntity<>(p1, staffHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(created1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID p1Id = created1.getBody().getData().id();

        CreatePatientRequest p2 = new CreatePatientRequest(
                DocumentType.CI, docNumber2, "Maria Elena", "Perez Gomez",
                LocalDate.of(1988, 8, 15), "F", BloodType.A_POS, sharedPhone, null, null, null, null
        );
        ResponseEntity<ApiResponse<PatientResponse>> created2 = restTemplate.exchange(
                "/api/v1/patients",
                HttpMethod.POST,
                new HttpEntity<>(p2, staffHeaders),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(created2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID p2Id = created2.getBody().getData().id();

        // 1. GET /patients devuelve 2 con displayName y sin cédula
        ResponseEntity<ApiResponse<List<BotPatientResponse>>> listResp = restTemplate.exchange(
                "/api/v1/bot/patients?phone=" + sharedPhone,
                HttpMethod.GET,
                new HttpEntity<>(botHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<BotPatientResponse> patients = listResp.getBody().getData();
        assertThat(patients).hasSize(2);
        assertThat(patients).extracting(BotPatientResponse::patientId).containsExactlyInAnyOrder(p1Id, p2Id);
        assertThat(patients).extracting(BotPatientResponse::displayName).containsExactlyInAnyOrder("Carlos P.", "Maria P.");

        // 2. verify con la cédula de uno → su id
        VerifyPatientRequest verifyReq1 = new VerifyPatientRequest(sharedPhone, docNumber1);
        ResponseEntity<ApiResponse<BotPatientResponse>> verifyResp1 = restTemplate.exchange(
                "/api/v1/bot/patients/verify",
                HttpMethod.POST,
                new HttpEntity<>(verifyReq1, botHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(verifyResp1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(verifyResp1.getBody().getData().patientId()).isEqualTo(p1Id);
        assertThat(verifyResp1.getBody().getData().displayName()).isEqualTo("Carlos P.");

        // 3. con una cédula ajena → 404
        VerifyPatientRequest verifyReqAlien = new VerifyPatientRequest(sharedPhone, "9999999999");
        ResponseEntity<ApiResponse<BotPatientResponse>> verifyRespAlien = restTemplate.exchange(
                "/api/v1/bot/patients/verify",
                HttpMethod.POST,
                new HttpEntity<>(verifyReqAlien, botHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(verifyRespAlien.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(verifyRespAlien.getBody().getMessage()).isEqualTo("Paciente no encontrado.");
    }
}
